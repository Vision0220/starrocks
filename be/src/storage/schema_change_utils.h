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

#include "common/statusor.h"
#include "storage/column_mapping.h"
#include "storage/convert_helper.h"
#include "storage/tablet_meta.h"
#include "storage/tablet_schema.h"

namespace starrocks {

struct AlterMaterializedViewParam {
    std::string column_name;
    std::string origin_column_name;
    std::string mv_expr;
};
using MaterializedViewParamMap = std::unordered_map<std::string, AlterMaterializedViewParam>;

class ChunkChanger {
public:
    ChunkChanger(const TabletSchema& tablet_schema);
    ~ChunkChanger();

    ColumnMapping* get_mutable_column_mapping(size_t column_index);

    const SchemaMapping& get_schema_mapping() const { return _schema_mapping; }

    std::vector<ColumnId>* get_mutable_selected_column_indexs() { return &_selected_column_indexs; }

    bool change_chunk(ChunkPtr& base_chunk, ChunkPtr& new_chunk, const TabletMetaSharedPtr& base_tablet_meta,
                      const TabletMetaSharedPtr& new_tablet_meta, MemPool* mem_pool);

    bool change_chunk_v2(ChunkPtr& base_chunk, ChunkPtr& new_chunk, const Schema& base_schema, const Schema& new_schema,
                         MemPool* mem_pool);

private:
    const MaterializeTypeConverter* get_materialize_type_converter(const std::string& materialized_function,
                                                                   LogicalType type);

    // @brief column-mapping specification of new schema
    SchemaMapping _schema_mapping;

    std::vector<ColumnId> _selected_column_indexs;

    DISALLOW_COPY(ChunkChanger);
};

class SchemaChangeUtils {
public:
    static void init_materialized_params(const TAlterTabletReqV2& request,
                                         MaterializedViewParamMap* materialized_view_param_map);

    static Status parse_request(const TabletSchema& base_schema, const TabletSchema& new_schema,
                                ChunkChanger* chunk_changer,
                                const MaterializedViewParamMap& materialized_view_param_map, bool has_delete_predicates,
                                bool* sc_sorting, bool* sc_directly);

private:
    // default_value for new column is needed
    static Status init_column_mapping(ColumnMapping* column_mapping, const TabletColumn& column_schema,
                                      const std::string& value);
};

} // namespace starrocks
