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

#pragma once

#include <memory>
#include <vector>

#include "common/statusor.h"
#include "gutil/macros.h"

namespace starrocks {
class MemTracker;
class SlotDescriptor;
class Chunk;
} // namespace starrocks

namespace starrocks::lake {

class DeltaWriterImpl;
class TabletManager;
class TabletWriter;

class DeltaWriter {
    using Chunk = starrocks::Chunk;

public:
    using Ptr = std::unique_ptr<DeltaWriter>;

    // for load
    // Does NOT take the ownership of |tablet_manager|、|slots| and |mem_tracker|
    static Ptr create(TabletManager* tablet_manager, int64_t tablet_id, int64_t txn_id, int64_t partition_id,
                      const std::vector<SlotDescriptor*>* slots, MemTracker* mem_tracker);

    // for schema change
    // Does NOT take the ownership of |tablet_manager| and |mem_tracker|
    static Ptr create(TabletManager* tablet_manager, int64_t tablet_id, int64_t max_buffer_size,
                      MemTracker* mem_tracker);

    explicit DeltaWriter(DeltaWriterImpl* impl) : _impl(impl) {}

    ~DeltaWriter();

    DISALLOW_COPY_AND_MOVE(DeltaWriter);

    // NOTE: It's ok to invoke this method in a bthread, there is no I/O operation in this method.
    [[nodiscard]] Status open();

    // NOTE: Do NOT invoke this method in a bthread.
    [[nodiscard]] Status write(const Chunk& chunk, const uint32_t* indexes, uint32_t indexes_size);

    // NOTE: Do NOT invoke this method in a bthread.
    [[nodiscard]] Status finish();

    // Manual flush, mainly used in UT
    // NOTE: Do NOT invoke this method in a bthread.
    [[nodiscard]] Status flush();

    // Manual flush, mainly used in UT
    // NOTE: Do NOT invoke this method in a bthread.
    [[nodiscard]] Status flush_async();

    // NOTE: Do NOT invoke this method in a bthread unless you are sure that `write()` has never been called.
    void close();

    [[nodiscard]] int64_t partition_id() const;

    [[nodiscard]] int64_t tablet_id() const;

    [[nodiscard]] int64_t txn_id() const;

    [[nodiscard]] MemTracker* mem_tracker();

    [[nodiscard]] TabletWriter* tablet_writer();

private:
    DeltaWriterImpl* _impl;
};

} // namespace starrocks::lake
