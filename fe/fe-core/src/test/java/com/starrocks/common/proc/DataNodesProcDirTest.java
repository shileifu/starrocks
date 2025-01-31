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
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/test/java/org/apache/doris/common/proc/BackendsProcDirTest.java

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

package com.starrocks.common.proc;

import com.starrocks.catalog.TabletInvertedIndex;
import com.starrocks.common.AnalysisException;
import com.starrocks.persist.EditLog;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.system.DataNode;
import com.starrocks.system.SystemInfoService;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DataNodesProcDirTest {
    private DataNode b1;
    private DataNode b2;

    @Mocked
    private SystemInfoService systemInfoService;
    @Mocked
    private TabletInvertedIndex tabletInvertedIndex;
    @Mocked
    private GlobalStateMgr globalStateMgr;
    @Mocked
    private EditLog editLog;

    @Before
    public void setUp() {
        b1 = new DataNode(1000, "host1", 10000);
        b1.updateOnce(10001, 10003, 10005);
        b2 = new DataNode(1001, "host2", 20000);
        b2.updateOnce(20001, 20003, 20005);

        new Expectations() {
            {
                editLog.logAddBackend((DataNode) any);
                minTimes = 0;

                editLog.logDropBackend((DataNode) any);
                minTimes = 0;

                editLog.logBackendStateChange((DataNode) any);
                minTimes = 0;

                globalStateMgr.getNextId();
                minTimes = 0;
                result = 10000L;

                globalStateMgr.getEditLog();
                minTimes = 0;
                result = editLog;

                globalStateMgr.clear();
                minTimes = 0;

                systemInfoService.getBackend(1000);
                minTimes = 0;
                result = b1;

                systemInfoService.getBackend(1001);
                minTimes = 0;
                result = b2;

                systemInfoService.getBackend(1002);
                minTimes = 0;
                result = null;

                tabletInvertedIndex.getTabletNumByBackendId(anyLong);
                minTimes = 0;
                result = 2;
            }
        };

        new Expectations(globalStateMgr) {
            {
                GlobalStateMgr.getCurrentState();
                minTimes = 0;
                result = globalStateMgr;

                GlobalStateMgr.getCurrentState();
                minTimes = 0;
                result = globalStateMgr;

                GlobalStateMgr.getCurrentInvertedIndex();
                minTimes = 0;
                result = tabletInvertedIndex;

                GlobalStateMgr.getCurrentSystemInfo();
                minTimes = 0;
                result = systemInfoService;
            }
        };

    }

    @After
    public void tearDown() {
        // systemInfoService = null;
    }

    @Test(expected = AnalysisException.class)
    public void testLookupNormal() throws AnalysisException {
        DataNodesProcDir dir;
        ProcNodeInterface node;

        dir = new DataNodesProcDir(systemInfoService);
        try {
            node = dir.lookup("1000");
            Assert.assertNotNull(node);
            Assert.assertTrue(node instanceof DataNodeProcNode);
        } catch (AnalysisException e) {
            e.printStackTrace();
            Assert.fail();
        }

        dir = new DataNodesProcDir(systemInfoService);
        try {
            node = dir.lookup("1001");
            Assert.assertNotNull(node);
            Assert.assertTrue(node instanceof DataNodeProcNode);
        } catch (AnalysisException e) {
            Assert.fail();
        }

        dir = new DataNodesProcDir(systemInfoService);
        node = dir.lookup("1002");
        Assert.fail();
    }

    @Test
    public void testLookupInvalid() {
        DataNodesProcDir dir;
        ProcNodeInterface node;

        dir = new DataNodesProcDir(systemInfoService);
        try {
            node = dir.lookup(null);
        } catch (AnalysisException e) {
            e.printStackTrace();
        }

        try {
            node = dir.lookup("");
        } catch (AnalysisException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFetchResultNormal() throws AnalysisException {
        DataNodesProcDir dir;
        ProcResult result;

        dir = new DataNodesProcDir(systemInfoService);
        result = dir.fetchResult();
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof BaseProcResult);
    }

    @Test    
    public void testIPTitle() {
        Assert.assertTrue(DataNodesProcDir.TITLE_NAMES.get(1).equals("IP"));
    }
}
