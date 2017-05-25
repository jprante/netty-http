/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client.internal;

import java.util.concurrent.ThreadFactory;

/**
 *
 */
public class HttpClientThreadFactory implements ThreadFactory {

    private int number = 0;

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "org-xbib-netty-http-client-pool-" + (number++));
        thread.setDaemon(true);
        return thread;
    }
}
