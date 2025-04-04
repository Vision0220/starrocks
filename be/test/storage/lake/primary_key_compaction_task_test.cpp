// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <fmt/format.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <memory>
#include <random>

#include "column/chunk.h"
#include "column/datum_tuple.h"
#include "column/fixed_length_column.h"
#include "column/schema.h"
#include "column/vectorized_fwd.h"
#include "common/logging.h"
#include "fs/fs_util.h"
#include "runtime/exec_env.h"
#include "runtime/mem_tracker.h"
#include "storage/chunk_helper.h"
#include "storage/lake/compaction_policy.h"
#include "storage/lake/delta_writer.h"
#include "storage/lake/fixed_location_provider.h"
#include "storage/lake/gc.h"
#include "storage/lake/horizontal_compaction_task.h"
#include "storage/lake/join_path.h"
#include "storage/lake/tablet.h"
#include "storage/lake/tablet_manager.h"
#include "storage/lake/tablet_reader.h"
#include "storage/lake/txn_log.h"
#include "storage/tablet_schema.h"
#include "testutil/assert.h"
#include "testutil/id_generator.h"

namespace starrocks::lake {

using VSchema = starrocks::Schema;
using VChunk = starrocks::Chunk;

class TestLocationProvider : public LocationProvider {
public:
    explicit TestLocationProvider(std::string dir) : _dir(dir) {}

    std::set<int64_t> owned_tablets() const override { return _owned_shards; }

    std::string root_location(int64_t tablet_id) const override { return _dir; }

    Status list_root_locations(std::set<std::string>* roots) const override {
        roots->insert(_dir);
        return Status::OK();
    }

    std::set<int64_t> _owned_shards;
    std::string _dir;
};

class PrimaryKeyHorizontalCompactionTest : public testing::Test {
public:
    PrimaryKeyHorizontalCompactionTest() {
        _tablet_manager = ExecEnv::GetInstance()->lake_tablet_manager();

        _parent_mem_tracker = std::make_unique<MemTracker>(-1);
        _mem_tracker = std::make_unique<MemTracker>(-1, "", _parent_mem_tracker.get());
        _location_provider = std::make_unique<TestLocationProvider>(kTestGroupPath);
        _backup_location_provider = _tablet_manager->TEST_set_location_provider(_location_provider.get());

        _tablet_metadata = std::make_shared<TabletMetadata>();
        _tablet_metadata->set_id(next_id());
        _tablet_metadata->set_version(1);
        _tablet_metadata->set_cumulative_point(0);
        _tablet_metadata->set_next_rowset_id(1);
        _location_provider->_owned_shards.insert(_tablet_metadata->id());
        //
        //  | column | type | KEY | NULL |
        //  +--------+------+-----+------+
        //  |   c0   |  INT | YES |  NO  |
        //  |   c1   |  INT | NO  |  NO  |
        auto schema = _tablet_metadata->mutable_schema();
        schema->set_id(next_id());
        schema->set_num_short_key_columns(1);
        schema->set_keys_type(PRIMARY_KEYS);
        schema->set_num_rows_per_row_block(65535);
        auto c0 = schema->add_column();
        {
            c0->set_unique_id(next_id());
            c0->set_name("c0");
            c0->set_type("INT");
            c0->set_is_key(true);
            c0->set_is_nullable(false);
        }
        auto c1 = schema->add_column();
        {
            c1->set_unique_id(next_id());
            c1->set_name("c1");
            c1->set_type("INT");
            c1->set_is_key(false);
            c1->set_is_nullable(false);
            c1->set_aggregation("REPLACE");
        }

        _tablet_schema = TabletSchema::create(*schema);
        _schema = std::make_shared<VSchema>(ChunkHelper::convert_schema(*_tablet_schema));
    }

protected:
    constexpr static const char* const kTestGroupPath = "test_lake_pk_compaction_task";
    constexpr static const int kChunkSize = 12;

    void SetUp() override {
        (void)ExecEnv::GetInstance()->lake_tablet_manager()->TEST_set_location_provider(_location_provider.get());
        (void)fs::remove_all(kTestGroupPath);
        CHECK_OK(fs::create_directories(lake::join_path(kTestGroupPath, lake::kSegmentDirectoryName)));
        CHECK_OK(fs::create_directories(lake::join_path(kTestGroupPath, lake::kMetadataDirectoryName)));
        CHECK_OK(fs::create_directories(lake::join_path(kTestGroupPath, lake::kTxnLogDirectoryName)));
        CHECK_OK(_tablet_manager->put_tablet_metadata(*_tablet_metadata));
    }

    void TearDown() override {
        ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(_tablet_metadata->id()));
        tablet.delete_txn_log(_txn_id);
        _txn_id++;
        (void)ExecEnv::GetInstance()->lake_tablet_manager()->TEST_set_location_provider(_backup_location_provider);
        (void)fs::remove_all(kTestGroupPath);
    }

    VChunk generate_data(int64_t chunk_size, int shift) {
        std::vector<int> v0(chunk_size);
        std::vector<int> v1(chunk_size);
        for (int i = 0; i < chunk_size; i++) {
            v0[i] = i + shift * chunk_size;
        }
        auto rng = std::default_random_engine{};
        std::shuffle(v0.begin(), v0.end(), rng);
        for (int i = 0; i < chunk_size; i++) {
            v1[i] = v0[i] * 3;
        }

        auto c0 = Int32Column::create();
        auto c1 = Int32Column::create();
        c0->append_numbers(v0.data(), v0.size() * sizeof(int));
        c1->append_numbers(v1.data(), v1.size() * sizeof(int));
        return VChunk({c0, c1}, _schema);
    }

    int64_t read(int64_t version) {
        ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(_tablet_metadata->id()));
        ASSIGN_OR_ABORT(auto reader, tablet.new_reader(version, *_schema));
        CHECK_OK(reader->prepare());
        CHECK_OK(reader->open(TabletReaderParams()));
        auto chunk = ChunkHelper::new_chunk(*_schema, 128);
        int64_t ret = 0;
        while (true) {
            auto st = reader->get_next(chunk.get());
            if (st.is_end_of_file()) {
                break;
            }
            CHECK_OK(st);
            ret += chunk->num_rows();
            chunk->reset();
        }
        return ret;
    }

    TabletManager* _tablet_manager;
    std::unique_ptr<MemTracker> _parent_mem_tracker;
    std::unique_ptr<MemTracker> _mem_tracker;
    std::unique_ptr<TestLocationProvider> _location_provider;
    LocationProvider* _backup_location_provider;
    std::shared_ptr<TabletMetadata> _tablet_metadata;
    std::shared_ptr<TabletSchema> _tablet_schema;
    std::shared_ptr<VSchema> _schema;
    int64_t _partition_id = 4561;
    int64_t _txn_id = 1231;
};

// each time overwrite last rows
TEST_F(PrimaryKeyHorizontalCompactionTest, test1) {
    // Prepare data for writing
    auto chunk0 = generate_data(kChunkSize, 0);
    auto indexes = std::vector<uint32_t>(kChunkSize);
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    auto version = 1;
    auto tablet_id = _tablet_metadata->id();
    for (int i = 0; i < 3; i++) {
        _txn_id++;
        auto delta_writer =
                DeltaWriter::create(_tablet_manager, tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->write(chunk0, indexes.data(), indexes.size()));
        ASSERT_OK(delta_writer->finish());
        delta_writer->close();
        // Publish version
        ASSERT_OK(_tablet_manager->publish_version(tablet_id, version, version + 1, &_txn_id, 1).status());
        version++;
    }
    ASSERT_EQ(kChunkSize, read(version));
    ASSIGN_OR_ABORT(auto new_tablet_metadata1, _tablet_manager->get_tablet_metadata(tablet_id, version));
    EXPECT_EQ(new_tablet_metadata1->rowsets_size(), 3);

    // make sure delvecs have been generated
    for (int i = 0; i < 3; i++) {
        if (i < 2) {
            EXPECT_TRUE(fs::path_exist(_location_provider->tablet_delvec_location(tablet_id, version - i)));
        } else {
            EXPECT_FALSE(fs::path_exist(_location_provider->tablet_delvec_location(tablet_id, version - i)));
        }
    }
    EXPECT_EQ(3, _tablet_manager->update_mgr()->cached_del_vec_size());

    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));
    _txn_id++;

    ASSIGN_OR_ABORT(auto task, _tablet_manager->compact(_tablet_metadata->id(), version, _txn_id));
    ASSERT_OK(task->execute(nullptr));
    ASSERT_OK(_tablet_manager->publish_version(_tablet_metadata->id(), version, version + 1, &_txn_id, 1).status());
    version++;
    ASSERT_EQ(kChunkSize, read(version));

    ASSIGN_OR_ABORT(auto new_tablet_metadata2, _tablet_manager->get_tablet_metadata(tablet_id, version));
    EXPECT_EQ(new_tablet_metadata2->rowsets_size(), 1);

    // make sure delvecs have been gc
    config::lake_gc_segment_expire_seconds = 0;
    config::lake_gc_metadata_max_versions = 1;
    ASSERT_OK(metadata_gc(kTestGroupPath, _tablet_manager, _txn_id + 1));
    ASSERT_OK(datafile_gc(kTestGroupPath, _tablet_manager));
    for (int ver = 1; ver <= version; ver++) {
        EXPECT_FALSE(fs::path_exist(_location_provider->tablet_delvec_location(tablet_id, ver)));
    }
    EXPECT_EQ(1, _tablet_manager->update_mgr()->cached_del_vec_size());
}

// test write 3 diff chunk
TEST_F(PrimaryKeyHorizontalCompactionTest, test2) {
    // Prepare data for writing
    std::vector<Chunk> chunks;
    for (int i = 0; i < 3; i++) {
        chunks.push_back(generate_data(kChunkSize, i));
    }
    auto indexes = std::vector<uint32_t>(kChunkSize);
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    auto version = 1;
    auto tablet_id = _tablet_metadata->id();
    for (int i = 0; i < 3; i++) {
        _txn_id++;
        auto delta_writer =
                DeltaWriter::create(_tablet_manager, tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->write(chunks[i], indexes.data(), indexes.size()));
        ASSERT_OK(delta_writer->finish());
        delta_writer->close();
        // Publish version
        ASSERT_OK(_tablet_manager->publish_version(tablet_id, version, version + 1, &_txn_id, 1).status());
        version++;
    }
    ASSERT_EQ(kChunkSize * 3, read(version));

    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));
    _txn_id++;

    ASSIGN_OR_ABORT(auto task, _tablet_manager->compact(_tablet_metadata->id(), version, _txn_id));
    ASSERT_OK(task->execute(nullptr));
    ASSERT_OK(_tablet_manager->publish_version(_tablet_metadata->id(), version, version + 1, &_txn_id, 1).status());
    version++;
    ASSERT_EQ(kChunkSize * 3, read(version));

    ASSIGN_OR_ABORT(auto new_tablet_metadata, _tablet_manager->get_tablet_metadata(tablet_id, version));
    EXPECT_EQ(new_tablet_metadata->rowsets_size(), 1);
}

// test write empty chunk
TEST_F(PrimaryKeyHorizontalCompactionTest, test3) {
    // Prepare data for writing
    std::vector<Chunk> chunks;
    for (int i = 0; i < 3; i++) {
        if (i == 1) {
            chunks.push_back(generate_data(0, 0));
        } else {
            chunks.push_back(generate_data(kChunkSize, i));
        }
    }
    auto indexes = std::vector<uint32_t>(kChunkSize);
    auto indexes_empty = std::vector<uint32_t>();
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    auto version = 1;
    auto tablet_id = _tablet_metadata->id();
    for (int i = 0; i < 3; i++) {
        _txn_id++;
        auto delta_writer =
                DeltaWriter::create(_tablet_manager, tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        if (i == 1) {
            ASSERT_OK(delta_writer->write(chunks[i], indexes_empty.data(), indexes_empty.size()));
        } else {
            ASSERT_OK(delta_writer->write(chunks[i], indexes.data(), indexes.size()));
        }
        ASSERT_OK(delta_writer->finish());
        delta_writer->close();
        // Publish version
        ASSERT_OK(_tablet_manager->publish_version(tablet_id, version, version + 1, &_txn_id, 1).status());
        version++;
    }
    ASSERT_EQ(kChunkSize * 2, read(version));

    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));
    _txn_id++;

    ASSIGN_OR_ABORT(auto task, _tablet_manager->compact(_tablet_metadata->id(), version, _txn_id));
    ASSERT_OK(task->execute(nullptr));
    ASSERT_OK(_tablet_manager->publish_version(_tablet_metadata->id(), version, version + 1, &_txn_id, 1).status());
    version++;
    ASSERT_EQ(kChunkSize * 2, read(version));

    ASSIGN_OR_ABORT(auto new_tablet_metadata, _tablet_manager->get_tablet_metadata(tablet_id, version));
    EXPECT_EQ(new_tablet_metadata->rowsets_size(), 1);
}

TEST_F(PrimaryKeyHorizontalCompactionTest, test_compaction_policy) {
    // Prepare data for writing
    std::vector<Chunk> chunks;
    for (int i = 0; i < 3; i++) {
        chunks.push_back(generate_data(kChunkSize, i));
    }
    auto indexes = std::vector<uint32_t>(kChunkSize);
    for (int i = 0; i < kChunkSize; i++) {
        indexes[i] = i;
    }

    auto version = 1;
    auto tablet_id = _tablet_metadata->id();
    for (int i = 0; i < 3; i++) {
        _txn_id++;
        auto delta_writer =
                DeltaWriter::create(_tablet_manager, tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->write(chunks[i], indexes.data(), indexes.size()));
        ASSERT_OK(delta_writer->finish());
        delta_writer->close();
        // Publish version
        ASSERT_OK(_tablet_manager->publish_version(tablet_id, version, version + 1, &_txn_id, 1).status());
        version++;
    }
    ASSERT_EQ(kChunkSize * 3, read(version));
    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));

    ASSIGN_OR_ABORT(auto compaction_policy,
                    CompactionPolicy::create_compaction_policy(std::make_shared<Tablet>(tablet)));
    ASSIGN_OR_ABORT(auto input_rowsets, compaction_policy->pick_rowsets(version));
    EXPECT_EQ(3, input_rowsets.size());

    config::max_update_compaction_num_singleton_deltas = 2;
    ASSIGN_OR_ABORT(auto input_rowsets2, compaction_policy->pick_rowsets(version));
    EXPECT_EQ(2, input_rowsets2.size());

    config::max_update_compaction_num_singleton_deltas = 1;
    ASSIGN_OR_ABORT(auto input_rowsets3, compaction_policy->pick_rowsets(version));
    EXPECT_EQ(1, input_rowsets3.size());
}

TEST_F(PrimaryKeyHorizontalCompactionTest, test_compaction_policy2) {
    // Prepare data for writing
    std::vector<Chunk> chunks;
    std::vector<std::vector<uint32_t>> indexes_list;
    for (int i = 0; i < 3; i++) {
        chunks.push_back(generate_data(kChunkSize * (i + 1), i));
        auto indexes = std::vector<uint32_t>(kChunkSize * (i + 1));
        for (int j = 0; j < kChunkSize * (i + 1); j++) {
            indexes[j] = j;
        }
        indexes_list.push_back(indexes);
    }

    auto version = 1;
    auto tablet_id = _tablet_metadata->id();
    for (int i = 0; i < 3; i++) {
        _txn_id++;
        auto delta_writer =
                DeltaWriter::create(_tablet_manager, tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->write(chunks[i], indexes_list[i].data(), indexes_list[i].size()));
        ASSERT_OK(delta_writer->finish());
        delta_writer->close();
        // Publish version
        ASSERT_OK(_tablet_manager->publish_version(tablet_id, version, version + 1, &_txn_id, 1).status());
        version++;
    }
    {
        _txn_id++;
        auto delta_writer =
                DeltaWriter::create(_tablet_manager, tablet_id, _txn_id, _partition_id, nullptr, _mem_tracker.get());
        ASSERT_OK(delta_writer->open());
        ASSERT_OK(delta_writer->write(chunks[0], indexes_list[0].data(), indexes_list[0].size()));
        ASSERT_OK(delta_writer->finish());
        delta_writer->close();
        // Publish version
        ASSERT_OK(_tablet_manager->publish_version(tablet_id, version, version + 1, &_txn_id, 1).status());
        version++;
    }
    ASSERT_EQ(kChunkSize * 6, read(version));
    ASSIGN_OR_ABORT(auto tablet, _tablet_manager->get_tablet(tablet_id));

    config::max_update_compaction_num_singleton_deltas = 4;
    ASSIGN_OR_ABORT(auto compaction_policy,
                    CompactionPolicy::create_compaction_policy(std::make_shared<Tablet>(tablet)));
    ASSIGN_OR_ABORT(auto input_rowsets, compaction_policy->pick_rowsets(version));
    EXPECT_EQ(4, input_rowsets.size());

    // check the rowset order, pick rowset#1 first, because it have deleted rows.
    // Next order is rowset#4#2#3, by their byte size.
    EXPECT_EQ(input_rowsets[0]->id(), 1);
    EXPECT_EQ(input_rowsets[1]->id(), 4);
    EXPECT_EQ(input_rowsets[2]->id(), 2);
    EXPECT_EQ(input_rowsets[3]->id(), 3);
}

} // namespace starrocks::lake
