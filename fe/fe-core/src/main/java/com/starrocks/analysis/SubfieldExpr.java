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


package com.starrocks.analysis;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.starrocks.catalog.Type;
import com.starrocks.common.AnalysisException;
import com.starrocks.sql.ast.AstVisitor;
import com.starrocks.thrift.TExprNode;
import com.starrocks.thrift.TExprNodeType;

import java.util.List;
import java.util.stream.Collectors;

public class SubfieldExpr extends Expr {

    // We use fieldNames to extract subfield column from children[0],
    // children[0] must be an StructType.
    private final ImmutableList<String> fieldNames;

    // Only used in parser, in parser, we can't determine column's type
    public SubfieldExpr(Expr child, ImmutableList<String> fieldNames) {
        this(child, null, fieldNames);
    }

    // In this constructor, we can determine column's type
    // child must be an StructType
    public SubfieldExpr(Expr child, Type type, ImmutableList<String> fieldNames) {
        if (type != null) {
            Preconditions.checkArgument(child.getType().isStructType());
        }
        children.add(child);
        this.type = type;
        this.fieldNames = fieldNames.stream().map(String::toLowerCase).collect(ImmutableList.toImmutableList());
    }

    public SubfieldExpr(SubfieldExpr other) {
        super(other);
        fieldNames = other.fieldNames;
    }

    public ImmutableList<String> getFieldNames() {
        return fieldNames;
    }

    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitSubfieldExpr(this, context);
    }

    @Override
    protected void analyzeImpl(Analyzer analyzer) throws AnalysisException {
        Preconditions.checkState(false, "unreachable");
    }

    @Override
    protected String toSqlImpl() {
        return getChild(0).toSqlImpl() + "." + Joiner.on('.').join(fieldNames);
    }

    @Override
    protected void toThrift(TExprNode msg) {
        msg.setNode_type(TExprNodeType.SUBFIELD_EXPR);
        msg.setUsed_subfield_names(fieldNames);
    }

    @Override
    public Expr clone() {
        return new SubfieldExpr(this);
    }
}
