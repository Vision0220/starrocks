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
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/planner/SchemaScanNode.java

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

package com.starrocks.planner;

import com.google.common.base.MoreObjects;
import com.starrocks.analysis.Analyzer;
import com.starrocks.analysis.TupleDescriptor;
import com.starrocks.common.UserException;
import com.starrocks.qe.ConnectContext;
import com.starrocks.thrift.TPlanNode;
import com.starrocks.thrift.TPlanNodeType;
import com.starrocks.thrift.TScanRangeLocations;
import com.starrocks.thrift.TSchemaScanNode;
import com.starrocks.thrift.TUserIdentity;

import java.util.List;

/**
 * Full scan of an SCHEMA table.
 */
public class SchemaScanNode extends ScanNode {
    private final String tableName;
    private String schemaDb;
    private String schemaTable;
    private String schemaWild;
    private String frontendIP;
    private int frontendPort;

    public void setSchemaDb(String schemaDb) {
        this.schemaDb = schemaDb;
    }

    public void setSchemaTable(String schemaTable) {
        this.schemaTable = schemaTable;
    }

    public String getSchemaDb() {
        return schemaDb;
    }

    public String getSchemaTable() {
        return schemaTable;
    }

    public void setUser(String user) {
    }

    public void setUserIp(String userIp) {
    }

    public void setFrontendIP(String frontendIP) {
        this.frontendIP = frontendIP;
    }

    public void setFrontendPort(int frontendPort) {
        this.frontendPort = frontendPort;
    }

    /**
     * Constructs node to scan given data files of table 'tbl'.
     */
    public SchemaScanNode(PlanNodeId id, TupleDescriptor desc) {
        super(id, desc, "SCAN SCHEMA");
        this.tableName = desc.getTable().getName();
    }

    @Override
    protected String debugString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        return helper.addValue(super.debugString()).toString();
    }

    @Override
    public void finalizeStats(Analyzer analyzer) throws UserException {
    }

    @Override
    protected void toThrift(TPlanNode msg) {
        msg.node_type = TPlanNodeType.SCHEMA_SCAN_NODE;
        msg.schema_scan_node = new TSchemaScanNode(desc.getId().asInt(), tableName);
        if (schemaDb != null) {
            msg.schema_scan_node.setDb(schemaDb);
        } else {
            if (tableName.equalsIgnoreCase("GLOBAL_VARIABLES")) {
                msg.schema_scan_node.setDb("GLOBAL");
            } else if (tableName.equalsIgnoreCase("SESSION_VARIABLES")) {
                msg.schema_scan_node.setDb("SESSION");
            }
        }

        if (schemaTable != null) {
            msg.schema_scan_node.setTable(schemaTable);
        }
        if (schemaWild != null) {
            msg.schema_scan_node.setWild(schemaWild);
        }

        ConnectContext ctx = ConnectContext.get();
        if (ctx != null) {
            msg.schema_scan_node.setThread_id(ConnectContext.get().getConnectionId());
        }
        msg.schema_scan_node.setIp(frontendIP);
        msg.schema_scan_node.setPort(frontendPort);

        TUserIdentity tCurrentUser = ConnectContext.get().getCurrentUserIdentity().toThrift();
        msg.schema_scan_node.setCurrent_user_ident(tCurrentUser);
    }

    /**
     * We query MySQL Meta to get request's data location
     * extra result info will pass to backend ScanNode
     */
    @Override
    public List<TScanRangeLocations> getScanRangeLocations(long maxScanRangeLength) {
        return null;
    }

    @Override
    public int getNumInstances() {
        return 1;
    }

    @Override
    public boolean canUsePipeLine() {
        return true;
    }

}
