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


package com.starrocks.sql.ast;

import com.starrocks.analysis.UserIdentity;
import com.starrocks.catalog.Column;
import com.starrocks.catalog.ScalarType;
import com.starrocks.qe.ShowResultSetMetaData;

/*
 *  SHOW ALL GRANTS;
 *      show all grants.
 *
 *  SHOW GRANTS:
 *      show grants of current user
 *
 *  SHOW GRANTS FOR user@'xxx';
 *      show grants for specified user identity
 */
//
// SHOW GRANTS;
// SHOW GRANTS FOR user@'xxx'
public class ShowGrantsStmt extends ShowStmt {

    private static final ShowResultSetMetaData META_DATA;

    static {
        ShowResultSetMetaData.Builder builder = ShowResultSetMetaData.builder();
        builder.addColumn(new Column("UserIdentity", ScalarType.createVarchar(100)));
        builder.addColumn(new Column("Grants", ScalarType.createVarchar(400)));
        META_DATA = builder.build();
    }

    private final boolean isAll;
    private UserIdentity userIdent;

    public ShowGrantsStmt(UserIdentity userIdent, boolean isAll) {
        this.userIdent = userIdent;
        this.isAll = isAll;
    }

    public UserIdentity getUserIdent() {
        return userIdent;
    }

    public boolean isAll() {
        return isAll;
    }

    public void setUserIdent(UserIdentity userIdent) {
        this.userIdent = userIdent;
    }

    @Override
    public ShowResultSetMetaData getMetaData() {
        return META_DATA;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitShowGrantsStatement(this, context);
    }

}
