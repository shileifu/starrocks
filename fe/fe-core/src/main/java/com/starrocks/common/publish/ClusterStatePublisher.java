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
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/common/publish/ClusterStatePublisher.java

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

package com.starrocks.common.publish;

import com.starrocks.common.ClientPool;
import com.starrocks.common.ThreadPoolManager;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.system.DataNode;
import com.starrocks.system.SystemInfoService;
import com.starrocks.thrift.BackendService;
import com.starrocks.thrift.TAgentPublishRequest;
import com.starrocks.thrift.TAgentResult;
import com.starrocks.thrift.TNetworkAddress;
import com.starrocks.thrift.TStatusCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

// This class intend to publish the state of cluster to backends.
public class ClusterStatePublisher {
    private static final Logger LOG = LogManager.getLogger(ClusterStatePublisher.class);
    private static ClusterStatePublisher INSTANCE;

    private ExecutorService executor =
            ThreadPoolManager.newDaemonFixedThreadPool(5, 256, "cluster-state-publisher", true);

    private SystemInfoService clusterInfoService;

    // Use public for unit test easily.
    public ClusterStatePublisher(SystemInfoService clusterInfoService) {
        this.clusterInfoService = clusterInfoService;
    }

    // Fuck singleton.
    public static ClusterStatePublisher getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClusterStatePublisher(GlobalStateMgr.getCurrentSystemInfo());
        }
        return INSTANCE;
    }

    public void publish(ClusterStateUpdate state, Listener listener, int timeoutMs) {
        Collection<DataNode> nodesToPublish = clusterInfoService.getIdToBackend().values();
        AckResponseHandler handler = new AckResponseHandler(nodesToPublish, listener);
        for (DataNode node : nodesToPublish) {
            executor.submit(new PublishWorker(state, node, handler));
        }
        try {
            if (!handler.awaitAllInMs(timeoutMs)) {
                DataNode[] backends = handler.pendingNodes();
                if (backends.length > 0) {
                    LOG.warn("timed out waiting for all nodes to publish. (pending nodes: {})",
                            Arrays.toString(backends));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public class PublishWorker implements Runnable {
        private ClusterStateUpdate stateUpdate;
        private DataNode node;
        private ResponseHandler handler;

        public PublishWorker(ClusterStateUpdate stateUpdate, DataNode node, ResponseHandler handler) {
            this.stateUpdate = stateUpdate;
            this.node = node;
            this.handler = handler;
        }

        @Override
        public void run() {
            // Here to publish all worker
            TNetworkAddress addr = new TNetworkAddress(node.getHost(), node.getBePort());
            BackendService.Client client = null;
            try {
                client = ClientPool.backendPool.borrowObject(addr);
            } catch (Exception e) {
                LOG.warn("Fetch a agent client failed. backend=[{}] reason=[{}]", addr, e);
                handler.onFailure(node, e);
                return;
            }
            try {
                TAgentPublishRequest request = stateUpdate.toThrift();
                TAgentResult tAgentResult = null;
                try {
                    tAgentResult = client.publish_cluster_state(request);
                } catch (TException e) {
                    // Ok, lets try another time
                    if (!ClientPool.backendPool.reopen(client)) {
                        // Failed another time, throw this
                        throw e;
                    }
                    tAgentResult = client.publish_cluster_state(request);
                }
                if (tAgentResult.getStatus().getStatus_code() != TStatusCode.OK) {
                    // Success execute, no dirty data possibility
                    LOG.warn("Backend execute publish failed. backend=[{}], message=[{}]",
                            addr, tAgentResult.getStatus().getError_msgs());
                }
                LOG.debug("Success publish to backend([{}])", addr);
                // Publish here
                handler.onResponse(node);
            } catch (TException e) {
                LOG.warn("A thrift exception happened when publish to a backend. backend=[{}], reason=[{}]", addr, e);
                handler.onFailure(node, e);
                ClientPool.backendPool.invalidateObject(addr, client);
                client = null;
            } finally {
                ClientPool.backendPool.returnObject(addr, client);
            }
        }
    }
}
