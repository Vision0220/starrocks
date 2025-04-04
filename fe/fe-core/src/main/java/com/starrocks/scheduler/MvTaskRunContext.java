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

package com.starrocks.scheduler;

import java.util.Map;
import java.util.Set;

public class MvTaskRunContext extends TaskRunContext {

    Map<String, Set<String>> baseToMvNameRef;
    Map<String, Set<String>> mvToBaseNameRef;

    String nextPartitionStart = null;
    String nextPartitionEnd = null;

    public MvTaskRunContext(TaskRunContext context) {
        this.ctx = context.ctx;
        this.definition = context.definition;
        this.remoteIp = context.remoteIp;
        this.properties = context.properties;
    }

    public Map<String, Set<String>> getBaseToMvNameRef() {
        return baseToMvNameRef;
    }

    public void setBaseToMvNameRef(Map<String, Set<String>> baseToMvNameRef) {
        this.baseToMvNameRef = baseToMvNameRef;
    }

    public Map<String, Set<String>> getMvToBaseNameRef() {
        return mvToBaseNameRef;
    }

    public void setMvToBaseNameRef(Map<String, Set<String>> mvToBaseNameRef) {
        this.mvToBaseNameRef = mvToBaseNameRef;
    }

    public boolean hasNextBatchPartition() {
        return nextPartitionStart != null && nextPartitionEnd != null;
    }

    public String getNextPartitionStart() {
        return nextPartitionStart;
    }

    public void setNextPartitionStart(String nextPartitionStart) {
        this.nextPartitionStart = nextPartitionStart;
    }

    public String getNextPartitionEnd() {
        return nextPartitionEnd;
    }

    public void setNextPartitionEnd(String nextPartitionEnd) {
        this.nextPartitionEnd = nextPartitionEnd;
    }
}
