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

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/analysis/MVColumnOneChildPattern.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.sql.analyzer.mvpattern;

import com.starrocks.analysis.Expr;
import com.starrocks.analysis.FunctionCallExpr;

public class MVColumnOneChildPattern implements MVColumnPattern {

    private String functionName;

    public MVColumnOneChildPattern(String functionName) {
        this.functionName = functionName;
    }

    @Override
    public boolean match(Expr expr) {
        if (!(expr instanceof FunctionCallExpr)) {
            return false;
        }
        FunctionCallExpr functionCallExpr = (FunctionCallExpr) expr;
        String exprFnName = functionCallExpr.getFnName().getFunction();
        if (!exprFnName.equalsIgnoreCase(functionName)) {
            return false;
        }
        if (functionCallExpr.getChildren().size() != 1) {
            return false;
        }
        if (functionCallExpr.getChild(0).unwrapSlotRef() == null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return functionName + "(column)";
    }
}
