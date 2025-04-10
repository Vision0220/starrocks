set(EXEC_FILES
        ./test_main.cpp
        ./column/array_column_test.cpp
        ./column/binary_column_test.cpp
        ./column/chunk_test.cpp
        ./column/column_helper_test.cpp
        ./column/column_pool_test.cpp
        ./column/const_column_test.cpp
        ./column/date_value_test.cpp
        ./column/decimalv3_column_test.cpp
        ./column/field_test.cpp
        ./column/fixed_length_column_test.cpp
        ./column/json_column_test.cpp
        ./column/map_column_test.cpp
        ./column/struct_column_test.cpp
        ./column/nullable_column_test.cpp
        ./column/object_column_test.cpp
        ./column/timestamp_value_test.cpp
        ./column/schema_test.cpp
        ./common/config_test.cpp
        ./common/status_test.cpp
        ./common/tracer_test.cpp
        ./common/s3_uri_test.cpp
        ./fs/fs_broker_test.cpp
        ./fs/fs_posix_test.cpp
        ./fs/fs_memory_test.cpp
        ./fs/fs_s3_test.cpp
        ./fs/fs_test.cpp
        ./fs/output_stream_wrapper_test.cpp
        ./exec/column_value_range_test.cpp
        ./exec/es/es_query_builder_test.cpp
        ./exec/es/es_scan_reader_test.cpp
        ./exec/es/es_scroll_parser_test.cpp
        ./exec/pipeline/pipeline_control_flow_test.cpp
        ./exec/pipeline/pipeline_driver_queue_test.cpp
        ./exec/pipeline/pipeline_file_scan_node_test.cpp
        ./exec/pipeline/pipeline_test_base.cpp
        ./exec/pipeline/query_context_manger_test.cpp
        ./exec/pipeline/query_context_test.cpp
        ./exec/pipeline/table_function_operator_test.cpp
        ./exec/query_cache/query_cache_test.cpp
        ./exec/query_cache/transform_operator.cpp
        ./exec/schema_columns_scanner_test.cpp
        ./exec/stream/mem_state_table_test.cpp
        ./exec/stream/stream_aggregator_test.cpp
        ./exec/stream/stream_operators_test.cpp
        ./exec/stream/stream_pipeline_test.cpp
        ./exec/tablet_info_test.cpp
        ./exec/agg_hash_map_test.cpp
        ./exec/analytor_test.cpp
        ./exec/analytor_test.cpp
        ./exec/arrow_converter_test.cpp
        ./exec/chunks_sorter_heap_sort_test.cpp
        ./exec/chunks_sorter_test.cpp
        ./exec/csv_scanner_test.cpp
        ./exec/file_scan_node_test.cpp
        ./exec/hdfs_scanner_test.cpp
        ./exec/hdfs_scan_node_test.cpp
        ./exec/join_hash_map_test.cpp
        ./exec/json_parser_test.cpp
        ./exec/json_scanner_test.cpp
        ./exec/parquet_scanner_test.cpp
        ./exec/repeat_node_test.cpp
        ./exec/sorting_test.cpp
        ./exec/table_function_node_test.cpp
        ./exprs/agg/json_each_test.cpp
        ./exprs/agg/aggregate_test.cpp
        ./exprs/arithmetic_expr_test.cpp
        ./exprs/arithmetic_operation_test.cpp
        ./exprs/array_element_expr_test.cpp
        ./exprs/decimal_binary_function_test.cpp
        ./exprs/array_expr_test.cpp
        ./exprs/array_functions_test.cpp
        ./exprs/binary_functions_test.cpp
        ./exprs/binary_predicate_test.cpp
        ./exprs/bitmap_functions_test.cpp
        ./exprs/case_expr_test.cpp
        ./exprs/cast_expr_test.cpp
        ./exprs/decimal_cast_expr_decimal_test.cpp
        ./exprs/decimal_cast_expr_integer_test.cpp
        ./exprs/decimal_cast_expr_float_test.cpp
        ./exprs/decimal_cast_expr_time_test.cpp
        ./exprs/decimal_cast_expr_decimalv2_test.cpp
        ./exprs/coalesce_expr_test.cpp
        ./exprs/compound_predicate_test.cpp
        ./exprs/condition_expr_test.cpp
        ./exprs/encryption_functions_test.cpp
        ./exprs/function_call_expr_test.cpp
        ./exprs/geography_functions_test.cpp
        ./exprs/hash_functions_test.cpp
        ./exprs/hyperloglog_functions_test.cpp
        ./exprs/if_expr_test.cpp
        ./exprs/function_helper_test.cpp
        ./exprs/in_predicate_test.cpp
        ./exprs/is_null_predicate_test.cpp
        ./exprs/json_functions_test.cpp
        ./exprs/lambda_function_expr_test.cpp
        ./exprs/like_test.cpp
        ./exprs/map_element_expr_test.cpp
        ./exprs/map_functions_test.cpp
        ./exprs/math_functions_test.cpp
        ./exprs/null_if_expr_test.cpp
        ./exprs/percentile_functions_test.cpp
        ./exprs/string_fn_concat_test.cpp
        ./exprs/string_fn_locate_test.cpp
        ./exprs/string_fn_pad_test.cpp
        ./exprs/string_fn_repeat_test.cpp
        ./exprs/string_fn_reverse_test.cpp
        ./exprs/string_fn_sm3_test.cpp
        ./exprs/string_fn_space_test.cpp
        ./exprs/string_fn_substr_test.cpp
        ./exprs/string_fn_test.cpp
        ./exprs/string_fn_trim_test.cpp
        ./exprs/string_fn_money_format_decimal_test.cpp
        ./exprs/struct_functions_test.cpp
        ./exprs/time_functions_test.cpp
        ./exprs/es_functions_test.cpp
        ./exprs/utility_functions_test.cpp
        ./exprs/runtime_filter_test.cpp
        ./exprs/subfield_expr_test.cpp
        ./formats/csv/array_converter_test.cpp
        ./formats/csv/binary_converter_test.cpp
        ./formats/csv/boolean_converter_test.cpp
        ./formats/csv/date_converter_test.cpp
        ./formats/csv/datetime_converter_test.cpp
        ./formats/csv/decimalv2_converter_test.cpp
        ./formats/csv/float_converter_test.cpp
        ./formats/csv/json_converter_test.cpp
        ./formats/csv/nullable_converter_test.cpp
        ./formats/csv/numeric_converter_test.cpp
        ./formats/json/binary_column_test.cpp
        ./formats/json/numeric_column_test.cpp
        ./formats/json/nullable_column_test.cpp
        ./formats/orc/orc_chunk_reader_test.cpp
        ./formats/orc/orc_lazy_load_test.cpp
        ./formats/orc/orc_test_util/MemoryOutputStream.cc
        ./formats/orc/orc_test_util/MemoryInputStream.cc
        ./formats/parquet/parquet_schema_test.cpp
        ./formats/parquet/encoding_test.cpp
        ./formats/parquet/page_reader_test.cpp
        ./formats/parquet/metadata_test.cpp
        ./formats/parquet/group_reader_test.cpp
        ./formats/parquet/file_reader_test.cpp
        ./geo/geo_types_test.cpp
        ./geo/wkt_parse_test.cpp
        ./http/http_client_test.cpp
        ./http/http_utils_test.cpp
        ./http/message_body_sink_test.cpp
        ./http/metrics_action_test.cpp
        ./http/stream_load_test.cpp
        ./http/transaction_stream_load_test.cpp
        ./io/array_input_stream_test.cpp
        ./io/compressed_input_stream_test.cpp
        ./io/fd_output_stream_test.cpp
        ./io/s3_output_stream_test.cpp
        ./io/s3_input_stream_test.cpp
        ./io/fd_input_stream_test.cpp
        ./io/seekable_input_stream_test.cpp
        ./storage/decimal12_test.cpp
        ./storage/disjunctive_predicates_test.cpp
        ./storage/utils_test.cpp
        ./storage/del_vector_test.cpp
        ./storage/delete_handler_test.cpp
        ./storage/file_utils_test.cpp
        ./storage/tablet_schema_map_test.cpp
        ./storage/hll_test.cpp
        ./storage/key_coder_test.cpp
        ./storage/kv_store_test.cpp
        ./storage/options_test.cpp
        ./storage/protobuf_file_test.cpp
        ./storage/page_cache_test.cpp
        ./storage/persistent_index_test.cpp
        ./storage/primary_index_test.cpp
        ./storage/primary_key_encoder_test.cpp
        ./storage/tablet_mgr_test.cpp
        ./storage/tablet_schema_helper.cpp
        ./storage/version_graph_test.cpp
        ./storage/lake/async_delta_writer_test.cpp
        ./storage/lake/compaction_policy_test.cpp
        ./storage/lake/delta_writer_test.cpp
        ./storage/lake/gc_test.cpp
        ./storage/lake/location_provider_test.cpp
        ./storage/lake/horizontal_compaction_task_test.cpp
        ./storage/lake/schema_change_test.cpp
        ./storage/lake/tablet_manager_test.cpp
        ./storage/lake/tablet_reader_test.cpp
        ./storage/lake/tablet_writer_test.cpp
        ./storage/lake/primary_key_compaction_task_test.cpp
        ./storage/lake/primary_key_test.cpp
        ./storage/rowset_update_state_test.cpp
        ./storage/rowset/rowset_test.cpp
        ./storage/rowset/binary_dict_page_test.cpp
        ./storage/rowset/binary_plain_page_test.cpp
        ./storage/rowset/binary_prefix_page_test.cpp
        ./storage/rowset/bitmap_index_test.cpp
        ./storage/rowset/bitshuffle_page_test.cpp
        ./storage/rowset/block_bloom_filter_test.cpp
        ./storage/rowset/bloom_filter_index_reader_writer_test.cpp
        ./storage/rowset/column_reader_writer_test.cpp
        ./storage/rowset/encoding_info_test.cpp
        ./storage/rowset/frame_of_reference_page_test.cpp
        ./storage/rowset/map_column_rw_test.cpp
        ./storage/rowset/ordinal_page_index_test.cpp
        ./storage/rowset/plain_page_test.cpp
        ./storage/rowset/rle_page_test.cpp
        ./storage/rowset/segment_rewriter_test.cpp
        ./storage/rowset/segment_test.cpp
        ./storage/rowset/segment_iterator_test.cpp
        ./storage/rowset/struct_column_rw_test.cpp
        ./storage/rowset/zone_map_index_test.cpp
        ./storage/rowset/unique_rowset_id_generator_test.cpp
        ./storage/rowset/default_value_column_iterator_test.cpp
        ./storage/rowset/index_page_test.cpp
        ./storage/snapshot_meta_test.cpp
        ./storage/short_key_index_test.cpp
        ./storage/storage_types_test.cpp
        ./storage/tablet_meta_test.cpp
        ./storage/tablet_meta_manager_test.cpp
        ./storage/table_reader_test.cpp
        ./storage/table_schema_test.cpp
        ./storage/tablet_updates_test.cpp
        ./storage/update_manager_test.cpp
        ./storage/compaction_utils_test.cpp
        ./storage/compaction_manager_test.cpp
        ./storage/default_compaction_policy_test.cpp
        ./storage/size_tiered_compaction_policy_test.cpp
        ./storage/aggregate_iterator_test.cpp
        ./storage/chunk_aggregator_test.cpp
        ./storage/chunk_helper_test.cpp
        ./storage/column_aggregator_test.cpp
        ./storage/column_predicate_test.cpp
        ./storage/conjunctive_predicates_test.cpp
        ./storage/convert_helper_test.cpp
        ./storage/merge_iterator_test.cpp
        ./storage/memtable_flush_executor_test.cpp
        ./storage/memtable_test.cpp
        ./storage/projection_iterator_test.cpp
        ./storage/push_handler_test.cpp
        ./storage/range_test.cpp
        ./storage/row_source_mask_test.cpp
        ./storage/union_iterator_test.cpp
        ./storage/unique_iterator_test.cpp
        ./storage/cumulative_compaction_test.cpp
        ./storage/base_compaction_test.cpp
        ./storage/rowset_merger_test.cpp
        ./storage/schema_change_test.cpp
        ./storage/storage_engine_test.cpp
        ./storage/binlog_file_test.cpp
        ./storage/publish_version_task_test.cpp
        ./runtime/buffer_control_block_test.cpp
        ./runtime/datetime_value_test.cpp
        ./runtime/decimalv2_value_test.cpp
        ./runtime/decimalv3_test.cpp
        ./runtime/external_scan_context_mgr_test.cpp
        ./runtime/fragment_mgr_test.cpp
        ./runtime/int128_arithmetic_ops_test.cpp
        ./runtime/kafka_consumer_pipe_test.cpp
        ./runtime/lake_tablets_channel_test.cpp
        ./runtime/large_int_value_test.cpp
        ./runtime/memory/mem_chunk_allocator_test.cpp
        ./runtime/memory/system_allocator_test.cpp
        ./runtime/memory/memory_resource_test.cpp
        ./runtime/mem_pool_test.cpp
        ./runtime/result_queue_mgr_test.cpp
        ./runtime/routine_load_task_executor_test.cpp
        ./runtime/small_file_mgr_test.cpp
        ./runtime/snapshot_loader_test.cpp
        ./runtime/stream_load_pipe_test.cpp
        ./runtime/string_value_test.cpp
        ./runtime/type_descriptor_test.cpp
        ./runtime/type_descriptor_test.cpp
        ./runtime/user_function_cache_test.cpp
        ./runtime/sorted_chunks_merger_test.cpp
        ./runtime/memory_scratch_sink_test_issue_8676.cpp
        ./runtime/memory_scratch_sink_test.cpp
        ./serde/column_array_serde_test.cpp
        ./serde/protobuf_serde_test.cpp
        ./simd/simd_test.cpp
        ./simd/simd_selector_test.cpp
        ./simd/simd_mulselector_test.cpp
        ./util/phmap_test.cpp
        ./util/aes_util_test.cpp
        ./util/bitmap_test.cpp
        ./util/bitmap_value_test.cpp
        ./util/bit_stream_utils_test.cpp
        ./util/bit_util_test.cpp
        ./util/block_compression_test.cpp
        ./util/blocking_queue_test.cpp
        ./util/brpc_stub_cache_test.cpp
        ./util/c_string_test.cpp
        ./util/cidr_test.cpp
        ./util/coding_test.cpp
        ./util/core_local_test.cpp
        ./util/countdown_latch_test.cpp
        ./util/crc32c_test.cpp
        ./util/dynamic_cache_test.cpp
        ./util/exception_stack_test.cpp
        ./util/faststring_test.cpp
        ./util/file_util_test.cpp
        ./util/filesystem_util_test.cpp
        ./util/frame_of_reference_coding_test.cpp
        ./util/json_util_test.cpp
        ./util/md5_test.cpp
        ./util/monotime_test.cpp
        ./util/mysql_row_buffer_test.cpp
        ./util/new_metrics_test.cpp
        ./util/parse_util_test.cpp
        ./util/path_trie_test.cpp
        ./util/path_util_test.cpp
        ./util/priority_queue_test.cpp
        ./util/rle_encoding_test.cpp
        ./util/runtime_profile_test.cpp
        ./util/scoped_cleanup_test.cpp
        ./util/string_parser_test.cpp
        ./util/string_util_test.cpp
        ./util/tdigest_test.cpp
        ./util/thread_test.cpp
        ./util/trace_test.cpp
        ./util/uid_util_test.cpp
        ./util/utf8_check_test.cpp
        ./util/buffered_stream_test.cpp
        ./util/int96_test.cpp
        ./util/bit_packing_test.cpp
        ./util/gc_helper_test.cpp
        ./util/lru_cache_test.cpp
        ./util/arrow/starrocks_column_to_arrow_test.cpp
        ./util/starrocks_metrics_test.cpp
        ./util/system_metrics_test.cpp
        ./util/ratelimit_test.cpp
        ./util/cpu_usage_info_test.cpp
        ./gutil/sysinfo-test.cc
        ./service/lake_service_test.cpp
        )

if ("${USE_STAROS}" STREQUAL "ON")
    list(APPEND EXEC_FILES ./fs/fs_starlet_test.cpp)
endif ()
if ("${WITH_BLOCK_CACHE}" STREQUAL "ON")
    list(APPEND EXEC_FILES ./block_cache/block_cache_test.cpp)
    list(APPEND EXEC_FILES ./io/cache_input_stream_test.cpp)
endif ()
# simdjson Runtime Implement Dispatch: https://github.com/simdjson/simdjson/blob/master/doc/implementation-selection.md#runtime-cpu-detection
# Unset architecture-specific flags to avoid breaking implement runtime dispatch.

if ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86" OR "${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86_64")
    set_source_files_properties(./exprs/json_functions_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx)
    set_source_files_properties(./exprs/json_functions_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx2)
    set_source_files_properties(./exprs/json_functions_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx)
    set_source_files_properties(./exprs/json_functions_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx2)
    set_source_files_properties(./formats/json/binary_column_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx2)
    set_source_files_properties(./formats/json/numeric_column_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx2)
    set_source_files_properties(./formats/json/nullable_column_test.cpp PROPERTIES COMPILE_FLAGS -mno-avx2)
endif ()

if (USE_AVX2)
    set(EXEC_FILES ${EXEC_FILES} ./column/avx_numeric_column_test.cpp)
endif ()

add_executable(starrocks_test ${EXEC_FILES})

TARGET_LINK_LIBRARIES(starrocks_test ${TEST_LINK_LIBS})
SET_TARGET_PROPERTIES(starrocks_test PROPERTIES COMPILE_FLAGS "-fno-access-control")

# =================================================
# bytes_test requires tcmalloc which conflicts with sanitizer.
# Thus we need a standalone binary without sanitizer to test it.
add_executable(bytes_test
        ./column/bytes_test.cpp
        ../src/service/mem_hook.cpp
        )

set_target_properties(bytes_test PROPERTIES
        COMPILE_OPTIONS
        "-lpthread;-std=gnu++17;-g;-fno-sanitize=address")

set_target_properties(bytes_test PROPERTIES
        LINK_OPTIONS
        "-static-libstdc++;-static-libgcc;-fno-sanitize=address")

TARGET_LINK_LIBRARIES(bytes_test
        pthread
        gtest
        tcmalloc
        )

# =================================================
# test cases must be compiled as a standalone binary
ADD_BE_TEST(storage/task/engine_storage_migration_task_test)

# you can simply add test case as single binary without adding `main`
# ADD_BE_TEST(exec/sorting_test)
