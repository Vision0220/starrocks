# Copyright 2021-present StarRocks, Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cmake_minimum_required(VERSION 2.8.12)

# where to put generated libraries
set(LIBRARY_OUTPUT_PATH "${BUILD_DIR}/src/exec")

# where to put generated binaries
set(EXECUTABLE_OUTPUT_PATH "${BUILD_DIR}/src/exec")

set(EXEC_FILES
    data_sink.cpp
    empty_set_node.cpp
    exec_node.cpp
    exchange_node.cpp
    scan_node.cpp
    select_node.cpp
    sort_exec_exprs.cpp
    olap_common.cpp
    tablet_info.cpp
    tablet_sink.cpp
    mysql_scanner.cpp
    es/es_predicate.cpp
    es/es_scan_reader.cpp
    es/es_scroll_query.cpp
    es/es_query_builder.cpp
    es/es_scroll_parser.cpp
    local_file_writer.cpp
    broker_writer.cpp
    plain_text_builder.cpp
    aggregator.cpp
    sorted_streaming_aggregator.cpp
    aggregate/agg_hash_variant.cpp
    aggregate/aggregate_base_node.cpp
    aggregate/aggregate_blocking_node.cpp
    aggregate/distinct_blocking_node.cpp
    aggregate/aggregate_streaming_node.cpp
    aggregate/distinct_streaming_node.cpp
    partition/chunks_partitioner.cpp
    analytic_node.cpp
    analytor.cpp
    csv_scanner.cpp
    tablet_scanner.cpp
    olap_scan_node.cpp
    olap_scan_prepare.cpp
    meta_scan_node.cpp
    olap_meta_scanner.cpp
    olap_meta_scan_node.cpp
    lake_meta_scanner.cpp
    lake_meta_scan_node.cpp
    hash_joiner.cpp
    hash_join_node.cpp
    join_hash_map.cpp
    topn_node.cpp
    chunks_sorter.cpp
    chunks_sorter_heap_sort.cpp
    chunks_sorter_topn.cpp
    chunks_sorter_full_sort.cpp
    cross_join_node.cpp
    union_node.cpp
    except_hash_set.cpp
    except_node.cpp
    file_scanner.cpp
    orc_scanner.cpp
    jni_scanner.cpp
    arrow_to_starrocks_converter.cpp
    arrow_to_json_converter.cpp
    parquet_scanner.cpp
    parquet_reader.cpp
    file_scan_node.cpp
    assert_num_rows_node.cpp
    intersect_hash_set.cpp
    intersect_node.cpp
    hdfs_scanner.cpp
    hdfs_scanner_orc.cpp
    hdfs_scanner_parquet.cpp
    hdfs_scanner_text.cpp
    json_scanner.cpp
    json_parser.cpp
    project_node.cpp
    dict_decode_node.cpp
    repeat_node.cpp
    table_function_node.cpp
    schema_scanner.cpp
    schema_scan_node.cpp
    iceberg/iceberg_delete_builder.cpp
    schema_scanner/schema_tables_scanner.cpp
    schema_scanner/schema_dummy_scanner.cpp
    schema_scanner/schema_schemata_scanner.cpp
    schema_scanner/schema_variables_scanner.cpp
    schema_scanner/schema_columns_scanner.cpp
    schema_scanner/schema_charsets_scanner.cpp
    schema_scanner/schema_collations_scanner.cpp
    schema_scanner/schema_statistics_scanner.cpp
    schema_scanner/schema_triggers_scanner.cpp
    schema_scanner/schema_events_scanner.cpp
    schema_scanner/schema_views_scanner.cpp
    schema_scanner/schema_materialized_views_scanner.cpp
    schema_scanner/schema_tasks_scanner.cpp
    schema_scanner/schema_task_runs_scanner.cpp
    schema_scanner/schema_user_privileges_scanner.cpp
    schema_scanner/schema_schema_privileges_scanner.cpp
    schema_scanner/schema_table_privileges_scanner.cpp
    schema_scanner/schema_tables_config_scanner.cpp
    schema_scanner/schema_helper.cpp
    jdbc_scanner.cpp
    sorting/compare_column.cpp
    sorting/merge_column.cpp
    sorting/merge_cascade.cpp
    sorting/sort_column.cpp
    sorting/sort_permute.cpp
    connector_scan_node.cpp
    pipeline/exchange/exchange_merge_sort_source_operator.cpp
    pipeline/exchange/exchange_sink_operator.cpp
    pipeline/exchange/exchange_source_operator.cpp
    pipeline/exchange/local_exchange.cpp
    pipeline/exchange/local_exchange_sink_operator.cpp
    pipeline/exchange/local_exchange_source_operator.cpp
    pipeline/exchange/multi_cast_local_exchange.cpp
    pipeline/exchange/sink_buffer.cpp
    pipeline/fragment_executor.cpp
    pipeline/operator.cpp
    pipeline/limit_operator.cpp
    pipeline/pipeline_builder.cpp
    pipeline/project_operator.cpp
    pipeline/dict_decode_operator.cpp
    pipeline/result_sink_operator.cpp
    pipeline/olap_table_sink_operator.cpp
    pipeline/scan/balanced_chunk_buffer.cpp
    pipeline/scan/chunk_source.cpp
    pipeline/scan/morsel.cpp
    pipeline/scan/scan_operator.cpp
    pipeline/scan/olap_chunk_source.cpp
    pipeline/scan/olap_scan_operator.cpp
    pipeline/scan/olap_scan_prepare_operator.cpp
    pipeline/scan/olap_scan_context.cpp
    pipeline/scan/connector_scan_operator.cpp
    stream/scan/stream_scan_operator.cpp
    pipeline/scan/meta_chunk_source.cpp
    pipeline/scan/meta_scan_operator.cpp
    pipeline/scan/meta_scan_prepare_operator.cpp
    pipeline/scan/olap_meta_scan_prepare_operator.cpp
    pipeline/scan/lake_meta_scan_prepare_operator.cpp
    pipeline/scan/olap_schema_chunk_source.cpp
    pipeline/scan/olap_schema_scan_operator.cpp
    pipeline/scan/olap_schema_scan_context.cpp
    pipeline/scan/morsel.cpp
    pipeline/scan/chunk_buffer_limiter.cpp
    pipeline/sink/file_sink_operator.cpp
    pipeline/sink/export_sink_operator.cpp
    pipeline/sink/mysql_table_sink_operator.cpp
    pipeline/sink/memory_scratch_sink_operator.cpp
    pipeline/select_operator.cpp
    pipeline/nljoin/nljoin_context.cpp
    pipeline/nljoin/nljoin_build_operator.cpp
    pipeline/nljoin/nljoin_probe_operator.cpp
    pipeline/sort/partition_sort_sink_operator.cpp
    pipeline/sort/local_partition_topn_sink.cpp
    pipeline/sort/local_partition_topn_source.cpp
    pipeline/sort/local_partition_topn_context.cpp
    pipeline/sort/local_merge_sort_source_operator.cpp
    pipeline/sort/sort_context.cpp
    pipeline/pipeline_driver_executor.cpp
    pipeline/pipeline_driver_queue.cpp
    pipeline/pipeline_driver_poller.cpp
    pipeline/pipeline_driver.cpp
    pipeline/exec_state_reporter.cpp
    pipeline/driver_limiter.cpp
    pipeline/fragment_context.cpp
    pipeline/query_context.cpp
    pipeline/stream_epoch_manager.cpp
    pipeline/stream_pipeline_driver.cpp
    pipeline/aggregate/aggregate_blocking_sink_operator.cpp
    pipeline/aggregate/aggregate_blocking_source_operator.cpp
    pipeline/aggregate/aggregate_streaming_sink_operator.cpp
    pipeline/aggregate/aggregate_streaming_source_operator.cpp
    pipeline/aggregate/aggregate_distinct_blocking_sink_operator.cpp
    pipeline/aggregate/aggregate_distinct_blocking_source_operator.cpp
    pipeline/aggregate/aggregate_distinct_streaming_sink_operator.cpp
    pipeline/aggregate/aggregate_distinct_streaming_source_operator.cpp
    pipeline/aggregate/sorted_aggregate_streaming_sink_operator.cpp
    pipeline/aggregate/sorted_aggregate_streaming_source_operator.cpp
    pipeline/aggregate/repeat/repeat_operator.cpp
    pipeline/analysis/analytic_sink_operator.cpp
    pipeline/analysis/analytic_source_operator.cpp
    pipeline/table_function_operator.cpp
    pipeline/assert_num_rows_operator.cpp
    pipeline/set/union_passthrough_operator.cpp
    pipeline/set/union_const_source_operator.cpp
    pipeline/hashjoin/hash_join_build_operator.cpp
    pipeline/hashjoin/hash_join_probe_operator.cpp
    pipeline/hashjoin/hash_joiner_factory.cpp
    pipeline/set/except_context.cpp
    pipeline/set/except_build_sink_operator.cpp
    pipeline/set/except_probe_sink_operator.cpp
    pipeline/set/except_output_source_operator.cpp
    pipeline/set/intersect_context.cpp
    pipeline/set/intersect_build_sink_operator.cpp
    pipeline/set/intersect_probe_sink_operator.cpp
    pipeline/set/intersect_output_source_operator.cpp
    pipeline/chunk_accumulate_operator.cpp
    pipeline/pipeline.cpp
    workgroup/work_group.cpp
    workgroup/scan_executor.cpp
    workgroup/scan_task_queue.cpp
    query_cache/multilane_operator.cpp
    query_cache/cache_operator.cpp
    query_cache/cache_manager.cpp
    query_cache/lane_arbiter.cpp
    query_cache/conjugate_operator.cpp
    query_cache/ticket_checker.cpp
    stream/state/mem_state_table.cpp
    stream/aggregate/agg_state_data.cpp
    stream/aggregate/stream_aggregator.cpp
    stream/aggregate/stream_aggregate_operator.cpp
    stream/stream_aggregate_node.cpp)

# simdjson Runtime Implement Dispatch: https://github.com/simdjson/simdjson/blob/master/doc/implementation-selection.md#runtime-cpu-detection
# Unset architecture-specific flags to avoid breaking implement runtime dispatch.
if ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86" OR "${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86_64")
    set_source_files_properties(json_scanner.cpp PROPERTIES COMPILE_FLAGS "-mno-avx -mno-avx2")
    set_source_files_properties(json_parser.cpp PROPERTIES COMPILE_FLAGS "-mno-avx -mno-avx2")
endif()

add_library(Exec STATIC
    ${EXEC_FILES}
)
