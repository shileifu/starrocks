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

import com.starrocks.system.DataNode;

// Helper class for Listener.
public abstract class Listeners {
    private static Listener NO_OP_LISTENER;

    // Helper to return a no operation listener.
    public static Listener noOpListener() {
        if (NO_OP_LISTENER == null) {
            NO_OP_LISTENER = new Listener() {
                @Override
                public void onResponse(DataNode node) {
                }

                @Override
                public void onFailure(DataNode node, Throwable t) {
                }
            };
        }
        return NO_OP_LISTENER;
    }

    public static Listener nullToNoOpListener(Listener listener) {
        return listener == null ? noOpListener() : listener;
    }
}
