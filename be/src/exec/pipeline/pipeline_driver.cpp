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

#include "exec/pipeline/pipeline_driver.h"

#include <sstream>

#include "column/chunk.h"
#include "common/statusor.h"
#include "exec/pipeline/pipeline_driver_executor.h"
#include "exec/pipeline/scan/olap_scan_operator.h"
#include "exec/pipeline/source_operator.h"
#include "exec/query_cache/cache_operator.h"
#include "exec/query_cache/lane_arbiter.h"
#include "exec/query_cache/multilane_operator.h"
#include "exec/query_cache/ticket_checker.h"
#include "exec/workgroup/work_group.h"
#include "runtime/exec_env.h"
#include "runtime/runtime_state.h"
#include "util/debug/query_trace.h"
#include "util/defer_op.h"

namespace starrocks::pipeline {

PipelineDriver::~PipelineDriver() noexcept {
    if (_workgroup != nullptr) {
        _workgroup->decr_num_running_drivers();
    }
}

Status PipelineDriver::prepare(RuntimeState* runtime_state) {
    _runtime_state = runtime_state;

    auto* prepare_timer = ADD_TIMER(_runtime_profile, "DriverPrepareTime");
    SCOPED_TIMER(prepare_timer);

    // TotalTime is reserved name
    _total_timer = ADD_TIMER(_runtime_profile, "DriverTotalTime");
    _active_timer = ADD_TIMER(_runtime_profile, "ActiveTime");
    _overhead_timer = ADD_TIMER(_runtime_profile, "OverheadTime");

    _schedule_timer = ADD_TIMER(_runtime_profile, "ScheduleTime");
    _schedule_counter = ADD_COUNTER(_runtime_profile, "ScheduleCount", TUnit::UNIT);
    _yield_by_time_limit_counter = ADD_COUNTER(_runtime_profile, "YieldByTimeLimit", TUnit::UNIT);
    _yield_by_preempt_counter = ADD_COUNTER(_runtime_profile, "YieldByPreempt", TUnit::UNIT);
    _block_by_precondition_counter = ADD_COUNTER(_runtime_profile, "BlockByPrecondition", TUnit::UNIT);
    _block_by_output_full_counter = ADD_COUNTER(_runtime_profile, "BlockByOutputFull", TUnit::UNIT);
    _block_by_input_empty_counter = ADD_COUNTER(_runtime_profile, "BlockByInputEmpty", TUnit::UNIT);

    _pending_timer = ADD_TIMER(_runtime_profile, "PendingTime");
    _precondition_block_timer = ADD_CHILD_TIMER(_runtime_profile, "PreconditionBlockTime", "PendingTime");
    _input_empty_timer = ADD_CHILD_TIMER(_runtime_profile, "InputEmptyTime", "PendingTime");
    _first_input_empty_timer = ADD_CHILD_TIMER(_runtime_profile, "FirstInputEmptyTime", "InputEmptyTime");
    _followup_input_empty_timer = ADD_CHILD_TIMER(_runtime_profile, "FollowupInputEmptyTime", "InputEmptyTime");
    _output_full_timer = ADD_CHILD_TIMER(_runtime_profile, "OutputFullTime", "PendingTime");
    _pending_finish_timer = ADD_CHILD_TIMER(_runtime_profile, "PendingFinishTime", "PendingTime");

    DCHECK(_state == DriverState::NOT_READY);

    auto* source_op = source_operator();
    // attach ticket_checker to both ScanOperator and SplitMorselQueue
    auto should_attach_ticket_checker = (dynamic_cast<ScanOperator*>(source_op) != nullptr) &&
                                        (dynamic_cast<SplitMorselQueue*>(_morsel_queue) != nullptr) &&
                                        _fragment_ctx->enable_cache();
    if (should_attach_ticket_checker) {
        auto* scan_op = dynamic_cast<ScanOperator*>(source_op);
        auto* split_morsel_queue = dynamic_cast<SplitMorselQueue*>(_morsel_queue);
        auto ticket_checker = std::make_shared<query_cache::TicketChecker>();
        scan_op->set_ticket_checker(ticket_checker);
        split_morsel_queue->set_ticket_checker(ticket_checker);
    }

    const auto use_cache = _fragment_ctx->enable_cache();
    source_op->add_morsel_queue(_morsel_queue);
    // fill OperatorWithDependency instances into _dependencies from _operators.
    DCHECK(_dependencies.empty());
    _dependencies.reserve(_operators.size());
    LocalRFWaitingSet all_local_rf_set;
    for (auto& op_ref : _operators) {
        auto op = op_ref;
        if (use_cache) {
            // For MultilaneOperator<HashJoinProbeOperator> and MultilaneOperator<NLJoinProbeOperator>, we must use the
            // internal operators wrapped in MultiOperators to construct _dependencies.
            if (auto multilane_op = std::dynamic_pointer_cast<query_cache::MultilaneOperator>(op_ref);
                multilane_op != nullptr) {
                op = multilane_op->get_internal_op(0);
            }
        }

        if (auto* op_with_dep = dynamic_cast<DriverDependencyPtr>(op.get())) {
            _dependencies.push_back(op_with_dep);
        }

        const auto& rf_set = op->rf_waiting_set();
        all_local_rf_set.insert(rf_set.begin(), rf_set.end());

        const auto* global_rf_collector = op->runtime_bloom_filters();
        if (global_rf_collector != nullptr) {
            for (const auto& [_, desc] : global_rf_collector->descriptors()) {
                if (!desc->skip_wait()) {
                    _global_rf_descriptors.emplace_back(desc);
                }
            }

            _global_rf_wait_timeout_ns = std::max(_global_rf_wait_timeout_ns, op->global_rf_wait_timeout_ns());
        }
    }

    if (!all_local_rf_set.empty()) {
        _runtime_profile->add_info_string("LocalRfWaitingSet", strings::Substitute("$0", all_local_rf_set.size()));
    }
    _local_rf_holders = fragment_ctx()->runtime_filter_hub()->gather_holders(all_local_rf_set);

    if (use_cache) {
        ssize_t cache_op_idx = -1;
        query_cache::CacheOperatorPtr cache_op = nullptr;
        for (auto i = 0; i < _operators.size(); ++i) {
            if (cache_op = std::dynamic_pointer_cast<query_cache::CacheOperator>(_operators[i]); cache_op != nullptr) {
                cache_op_idx = i;
                break;
            }
        }

        if (cache_op != nullptr) {
            query_cache::LaneArbiterPtr lane_arbiter = cache_op->lane_arbiter();
            query_cache::MultilaneOperators multilane_operators;
            for (auto i = 0; i < cache_op_idx; ++i) {
                auto& op = _operators[i];
                if (auto* multilane_op = dynamic_cast<query_cache::MultilaneOperator*>(op.get());
                    multilane_op != nullptr) {
                    multilane_op->set_lane_arbiter(lane_arbiter);
                    multilane_operators.push_back(multilane_op);
                } else if (auto* olap_scan_op = dynamic_cast<OlapScanOperator*>(op.get()); olap_scan_op != nullptr) {
                    olap_scan_op->set_lane_arbiter(lane_arbiter);
                    olap_scan_op->set_cache_operator(cache_op);
                    cache_op->set_scan_operator(olap_scan_op);
                }
            }
            cache_op->set_multilane_operators(std::move(multilane_operators));
        }
    }

    for (auto& op : _operators) {
        int64_t time_spent = 0;
        {
            SCOPED_RAW_TIMER(&time_spent);
            RETURN_IF_ERROR(op->prepare(runtime_state));
        }
        op->set_prepare_time(time_spent);

        _operator_stages[op->get_id()] = OperatorStage::PREPARED;
    }

    // Driver has no dependencies always sets _all_dependencies_ready to true;
    _all_dependencies_ready = _dependencies.empty();
    // Driver has no local rf to wait for completion always sets _all_local_rf_ready to true;
    _all_local_rf_ready = _local_rf_holders.empty();
    // Driver has no global rf to wait for completion always sets _all_global_rf_ready_or_timeout to true;
    _all_global_rf_ready_or_timeout = _global_rf_descriptors.empty();
    set_driver_state(DriverState::READY);

    _total_timer_sw = runtime_state->obj_pool()->add(new MonotonicStopWatch());
    _pending_timer_sw = runtime_state->obj_pool()->add(new MonotonicStopWatch());
    _precondition_block_timer_sw = runtime_state->obj_pool()->add(new MonotonicStopWatch());
    _input_empty_timer_sw = runtime_state->obj_pool()->add(new MonotonicStopWatch());
    _output_full_timer_sw = runtime_state->obj_pool()->add(new MonotonicStopWatch());
    _pending_finish_timer_sw = runtime_state->obj_pool()->add(new MonotonicStopWatch());
    _total_timer_sw->start();
    _pending_timer_sw->start();
    _precondition_block_timer_sw->start();
    _input_empty_timer_sw->start();
    _output_full_timer_sw->start();
    _pending_finish_timer_sw->start();

    return Status::OK();
}

static inline bool is_multilane(pipeline::OperatorPtr& op) {
    if (dynamic_cast<query_cache::MultilaneOperator*>(op.get()) != nullptr) {
        return true;
    }
    // In essence, CacheOperator is also special MultilaneOperator semantically, it also needs handle EOS chunk.
    // it shall populate cache on receiving EOS chunk of a tablet.
    if (dynamic_cast<query_cache::CacheOperator*>(op.get()) != nullptr) {
        return true;
    }
    return false;
}

StatusOr<DriverState> PipelineDriver::process(RuntimeState* runtime_state, int worker_id) {
    COUNTER_UPDATE(_schedule_counter, 1);
    SCOPED_TIMER(_active_timer);
    QUERY_TRACE_SCOPED("process", _driver_name);
    set_driver_state(DriverState::RUNNING);
    size_t total_chunks_moved = 0;
    size_t total_rows_moved = 0;
    int64_t time_spent = 0;
    Status return_status = Status::OK();
    DeferOp defer([&]() { _update_statistics(total_chunks_moved, total_rows_moved, time_spent); });
    while (true) {
        RETURN_IF_LIMIT_EXCEEDED(runtime_state, "Pipeline");

        size_t num_chunks_moved = 0;
        bool should_yield = false;
        size_t num_operators = _operators.size();
        size_t new_first_unfinished = _first_unfinished;
        for (size_t i = _first_unfinished; i < num_operators - 1; ++i) {
            {
                SCOPED_RAW_TIMER(&time_spent);
                auto& curr_op = _operators[i];
                auto& next_op = _operators[i + 1];

                // Check curr_op finished firstly
                if (curr_op->is_finished()) {
                    if (i == 0) {
                        // For source operators
                        RETURN_IF_ERROR(return_status = _mark_operator_finishing(curr_op, runtime_state));
                    }
                    RETURN_IF_ERROR(return_status = _mark_operator_finishing(next_op, runtime_state));
                    new_first_unfinished = i + 1;
                    continue;
                }

                // try successive operator pairs
                if (!curr_op->has_output() || !next_op->need_input()) {
                    continue;
                }

                if (_check_fragment_is_canceled(runtime_state)) {
                    return _state;
                }

                // pull chunk from current operator and push the chunk onto next
                // operator
                StatusOr<ChunkPtr> maybe_chunk;
                {
                    SCOPED_TIMER(curr_op->_pull_timer);
                    QUERY_TRACE_SCOPED(curr_op->get_name(), "pull_chunk");
                    maybe_chunk = curr_op->pull_chunk(runtime_state);
                }
                return_status = maybe_chunk.status();
                if (!return_status.ok() && !return_status.is_end_of_file()) {
                    LOG(WARNING) << "pull_chunk returns not ok status " << return_status.to_string();
                    return return_status;
                }

                if (_check_fragment_is_canceled(runtime_state)) {
                    return _state;
                }

                if (return_status.ok()) {
                    if (maybe_chunk.value() &&
                        (maybe_chunk.value()->num_rows() > 0 ||
                         (maybe_chunk.value()->owner_info().is_last_chunk() && is_multilane(next_op)))) {
                        size_t row_num = maybe_chunk.value()->num_rows();
                        total_rows_moved += row_num;
                        {
                            SCOPED_TIMER(next_op->_push_timer);
                            QUERY_TRACE_SCOPED(next_op->get_name(), "push_chunk");
                            return_status = next_op->push_chunk(runtime_state, maybe_chunk.value());
                        }
                        // ignore empty chunk generated by per-tablet computation when query cache enabled
                        if (row_num > 0L) {
                            COUNTER_UPDATE(curr_op->_pull_row_num_counter, row_num);
                            COUNTER_UPDATE(curr_op->_pull_chunk_num_counter, 1);
                            COUNTER_UPDATE(next_op->_push_chunk_num_counter, 1);
                            COUNTER_UPDATE(next_op->_push_row_num_counter, row_num);
                        }

                        if (!return_status.ok() && !return_status.is_end_of_file()) {
                            LOG(WARNING) << "push_chunk returns not ok status " << return_status.to_string();
                            return return_status;
                        }
                    }
                    num_chunks_moved += 1;
                    total_chunks_moved += 1;
                }

                // Check curr_op finished again
                if (curr_op->is_finished()) {
                    // TODO: need add control flag
                    if (i == 0) {
                        // For source operators
                        RETURN_IF_ERROR(return_status = _mark_operator_finishing(curr_op, runtime_state));
                    }
                    RETURN_IF_ERROR(return_status = _mark_operator_finishing(next_op, runtime_state));
                    new_first_unfinished = i + 1;
                    continue;
                }
            }
            // yield when total chunks moved or time spent on-core for evaluation
            // exceed the designated thresholds.
            if (time_spent >= YIELD_MAX_TIME_SPENT) {
                should_yield = true;
                COUNTER_UPDATE(_yield_by_time_limit_counter, 1);
                break;
            }
            if (_workgroup != nullptr && time_spent >= YIELD_PREEMPT_MAX_TIME_SPENT &&
                _workgroup->driver_sched_entity()->in_queue()->should_yield(this, time_spent)) {
                should_yield = true;
                COUNTER_UPDATE(_yield_by_preempt_counter, 1);
                break;
            }
        }
        // close finished operators and update _first_unfinished index
        for (auto i = _first_unfinished; i < new_first_unfinished; ++i) {
            RETURN_IF_ERROR(return_status = _mark_operator_finished(_operators[i], runtime_state));
        }
        _first_unfinished = new_first_unfinished;

        if (sink_operator()->is_finished()) {
            finish_operators(runtime_state);
            set_driver_state(is_still_pending_finish() ? DriverState::PENDING_FINISH : DriverState::FINISH);
            return _state;
        }

        // no chunk moved in current round means that the driver is blocked.
        // should yield means that the CPU core is occupied the driver for a
        // very long time so that the driver should switch off the core and
        // give chance for another ready driver to run.
        if (num_chunks_moved == 0 || should_yield) {
            if (is_precondition_block()) {
                set_driver_state(DriverState::PRECONDITION_BLOCK);
                COUNTER_UPDATE(_block_by_precondition_counter, 1);
            } else if (!sink_operator()->is_finished() && !sink_operator()->need_input()) {
                set_driver_state(DriverState::OUTPUT_FULL);
                COUNTER_UPDATE(_block_by_output_full_counter, 1);
            } else if (!source_operator()->is_finished() && !source_operator()->has_output()) {
                set_driver_state(DriverState::INPUT_EMPTY);
                COUNTER_UPDATE(_block_by_input_empty_counter, 1);
            } else {
                set_driver_state(DriverState::READY);
            }
            return _state;
        }
    }
}

void PipelineDriver::check_short_circuit() {
    int last_finished = -1;
    for (int i = _first_unfinished; i < _operators.size() - 1; i++) {
        if (_operators[i]->is_finished()) {
            last_finished = i;
        }
    }

    if (last_finished == -1) {
        return;
    }

    _mark_operator_finishing(_operators[last_finished + 1], _runtime_state);
    for (auto i = _first_unfinished; i <= last_finished; ++i) {
        _mark_operator_finished(_operators[i], _runtime_state);
    }
    _first_unfinished = last_finished + 1;

    if (sink_operator()->is_finished()) {
        finish_operators(_runtime_state);
        set_driver_state(is_still_pending_finish() ? DriverState::PENDING_FINISH : DriverState::FINISH);
    }
}

void PipelineDriver::mark_precondition_not_ready() {
    for (auto& op : _operators) {
        _operator_stages[op->get_id()] = OperatorStage::PRECONDITION_NOT_READY;
    }
}

void PipelineDriver::mark_precondition_ready(RuntimeState* runtime_state) {
    for (auto& op : _operators) {
        op->set_precondition_ready(runtime_state);
        submit_operators();
    }
}

void PipelineDriver::submit_operators() {
    for (auto& op : _operators) {
        _operator_stages[op->get_id()] = OperatorStage::PROCESSING;
    }
}

void PipelineDriver::finish_operators(RuntimeState* runtime_state) {
    for (auto& op : _operators) {
        _mark_operator_finished(op, runtime_state);
    }
}

void PipelineDriver::cancel_operators(RuntimeState* runtime_state) {
    for (auto& op : _operators) {
        _mark_operator_cancelled(op, runtime_state);
    }
}

void PipelineDriver::_close_operators(RuntimeState* runtime_state) {
    for (auto& op : _operators) {
        _mark_operator_closed(op, runtime_state);
    }
}

void PipelineDriver::finalize(RuntimeState* runtime_state, DriverState state) {
    int64_t time_spent = 0;
    // The driver may be destructed after finalizing, so use a temporal driver to record
    // the information about the driver queue and workgroup.
    PipelineDriver copied_driver;
    copied_driver.set_workgroup(_workgroup);
    copied_driver.set_in_queue(_in_queue);
    copied_driver.set_driver_queue_level(_driver_queue_level);
    DeferOp defer([&copied_driver, &time_spent]() {
        copied_driver._update_driver_acct(0, 0, time_spent);
        copied_driver._in_queue->update_statistics(&copied_driver);
    });
    SCOPED_RAW_TIMER(&time_spent);

    VLOG_ROW << "[Driver] finalize, driver=" << this;
    DCHECK(state == DriverState::FINISH || state == DriverState::CANCELED || state == DriverState::INTERNAL_ERROR);
    QUERY_TRACE_BEGIN("finalize", _driver_name);
    _close_operators(runtime_state);

    set_driver_state(state);

    COUNTER_UPDATE(_total_timer, _total_timer_sw->elapsed_time());
    COUNTER_UPDATE(_schedule_timer, _total_timer->value() - _active_timer->value() - _pending_timer->value());
    _update_overhead_timer();

    // Acquire the pointer to avoid be released when removing query
    auto query_trace = _query_ctx->shared_query_trace();
    const std::string driver_name = _driver_name;
    _pipeline->count_down_driver(runtime_state);
    QUERY_TRACE_END("finalize", driver_name);
}

void PipelineDriver::_update_overhead_timer() {
    int64_t overhead_time = _active_timer->value();
    RuntimeProfile* profile = _runtime_profile.get();
    std::vector<RuntimeProfile*> operator_profiles;
    profile->get_children(&operator_profiles);
    for (auto* operator_profile : operator_profiles) {
        auto* common_metrics = operator_profile->get_child("CommonMetrics");
        DCHECK(common_metrics != nullptr);
        auto* total_timer = common_metrics->get_counter("OperatorTotalTime");
        DCHECK(total_timer != nullptr);
        overhead_time -= total_timer->value();
    }

    if (overhead_time < 0) {
        // All the time are recorded indenpendently, and there may be errors
        COUNTER_UPDATE(_overhead_timer, 0);
    } else {
        COUNTER_UPDATE(_overhead_timer, overhead_time);
    }
}

std::string PipelineDriver::to_readable_string() const {
    std::stringstream ss;
    ss << "driver=" << this << ", status=" << ds_to_string(this->driver_state()) << ", operator-chain: [";
    for (size_t i = 0; i < _operators.size(); ++i) {
        if (i == 0) {
            ss << _operators[i]->get_name();
        } else {
            ss << " -> " << _operators[i]->get_name();
        }
    }
    ss << "]";
    return ss.str();
}

workgroup::WorkGroup* PipelineDriver::workgroup() {
    return _workgroup.get();
}

const workgroup::WorkGroup* PipelineDriver::workgroup() const {
    return _workgroup.get();
}

void PipelineDriver::set_workgroup(workgroup::WorkGroupPtr wg) {
    _workgroup = std::move(wg);
    if (_workgroup == nullptr) {
        return;
    }
    _workgroup->incr_num_running_drivers();
}

bool PipelineDriver::_check_fragment_is_canceled(RuntimeState* runtime_state) {
    if (_fragment_ctx->is_canceled()) {
        cancel_operators(runtime_state);
        // If the fragment is cancelled after the source operator commits an i/o task to i/o threads,
        // the driver cannot be finished immediately and should wait for the completion of the pending i/o task.
        if (is_still_pending_finish()) {
            set_driver_state(DriverState::PENDING_FINISH);
        } else {
            set_driver_state(_fragment_ctx->final_status().ok() ? DriverState::FINISH : DriverState::CANCELED);
        }

        return true;
    }

    return false;
}

Status PipelineDriver::_mark_operator_finishing(OperatorPtr& op, RuntimeState* state) {
    auto& op_state = _operator_stages[op->get_id()];
    if (op_state >= OperatorStage::FINISHING) {
        return Status::OK();
    }

    VLOG_ROW << strings::Substitute("[Driver] finishing operator [fragment_id=$0] [driver=$1] [operator=$2]",
                                    print_id(state->fragment_instance_id()), to_readable_string(), op->get_name());
    {
        SCOPED_TIMER(op->_finishing_timer);
        op_state = OperatorStage::FINISHING;
        QUERY_TRACE_SCOPED(op->get_name(), "set_finishing");
        return op->set_finishing(state);
    }
}

Status PipelineDriver::_mark_operator_finished(OperatorPtr& op, RuntimeState* state) {
    RETURN_IF_ERROR(_mark_operator_finishing(op, state));
    auto& op_state = _operator_stages[op->get_id()];
    if (op_state >= OperatorStage::FINISHED) {
        return Status::OK();
    }

    VLOG_ROW << strings::Substitute("[Driver] finished operator [fragment_id=$0] [driver=$1] [operator=$2]",
                                    print_id(state->fragment_instance_id()), to_readable_string(), op->get_name());
    {
        SCOPED_TIMER(op->_finished_timer);
        op_state = OperatorStage::FINISHED;
        QUERY_TRACE_SCOPED(op->get_name(), "set_finished");
        return op->set_finished(state);
    }
}

Status PipelineDriver::_mark_operator_cancelled(OperatorPtr& op, RuntimeState* state) {
    Status res = _mark_operator_finished(op, state);
    if (!res.ok()) {
        LOG(WARNING) << fmt::format("fragment_id {} driver {} cancels operator {} with finished error {}",
                                    print_id(state->fragment_instance_id()), to_readable_string(), op->get_name(),
                                    res.get_error_msg());
    }
    auto& op_state = _operator_stages[op->get_id()];
    if (op_state >= OperatorStage::CANCELLED) {
        return Status::OK();
    }

    VLOG_ROW << strings::Substitute("[Driver] cancelled operator [fragment_id=$0] [driver=$1] [operator=$2]",
                                    print_id(state->fragment_instance_id()), to_readable_string(), op->get_name());
    op_state = OperatorStage::CANCELLED;
    return op->set_cancelled(state);
}

Status PipelineDriver::_mark_operator_closed(OperatorPtr& op, RuntimeState* state) {
    if (_fragment_ctx->is_canceled()) {
        RETURN_IF_ERROR(_mark_operator_cancelled(op, state));
    } else {
        RETURN_IF_ERROR(_mark_operator_finished(op, state));
    }

    auto& op_state = _operator_stages[op->get_id()];
    if (op_state >= OperatorStage::CLOSED) {
        return Status::OK();
    }

    VLOG_ROW << strings::Substitute("[Driver] close operator [fragment_id=$0] [driver=$1] [operator=$2]",
                                    print_id(state->fragment_instance_id()), to_readable_string(), op->get_name());
    {
        SCOPED_TIMER(op->_close_timer);
        op_state = OperatorStage::CLOSED;
        QUERY_TRACE_SCOPED(op->get_name(), "close");
        op->close(state);
    }
    COUNTER_UPDATE(op->_total_timer, op->_pull_timer->value() + op->_push_timer->value() +
                                             op->_finishing_timer->value() + op->_finished_timer->value() +
                                             op->_close_timer->value());
    return Status::OK();
}

void PipelineDriver::_update_driver_acct(size_t total_chunks_moved, size_t total_rows_moved, size_t time_spent) {
    driver_acct().update_last_chunks_moved(total_chunks_moved);
    driver_acct().update_accumulated_rows_moved(total_rows_moved);
    driver_acct().update_last_time_spent(time_spent);
}

void PipelineDriver::_update_statistics(size_t total_chunks_moved, size_t total_rows_moved, size_t time_spent) {
    _update_driver_acct(total_chunks_moved, total_rows_moved, time_spent);

    // Update statistics of scan operator
    if (ScanOperator* scan = source_scan_operator()) {
        query_ctx()->incr_cur_scan_rows_num(scan->get_last_scan_rows_num());
        query_ctx()->incr_cur_scan_bytes(scan->get_last_scan_bytes());
    }

    // Update cpu cost of this query
    int64_t runtime_ns = driver_acct().get_last_time_spent();
    int64_t source_operator_last_cpu_time_ns = source_operator()->get_last_growth_cpu_time_ns();
    int64_t sink_operator_last_cpu_time_ns = sink_operator()->get_last_growth_cpu_time_ns();
    int64_t accounted_cpu_cost = runtime_ns + source_operator_last_cpu_time_ns + sink_operator_last_cpu_time_ns;
    query_ctx()->incr_cpu_cost(accounted_cpu_cost);
}

void PipelineDriver::increment_schedule_times() {
    driver_acct().increment_schedule_times();
}

} // namespace starrocks::pipeline
