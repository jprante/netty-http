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
package org.xbib.netty.http.client;

import io.netty.handler.codec.http.HttpVersion;
import org.xbib.netty.http.client.internal.HttpClientUserAgent;

import java.net.URI;

/**
 */
public interface HttpRequestDefaults {

    HttpVersion DEFAULT_HTTP_VERSION = HttpVersion.HTTP_1_1;

    String DEFAULT_USER_AGENT = HttpClientUserAgent.getUserAgent();

    URI DEFAULT_URI = URI.create("http://localhost");

    boolean DEFAULT_GZIP = true;

    boolean DEFAULT_FOLLOW_REDIRECT = true;

    int DEFAULT_TIMEOUT_MILLIS = 5000;

    int DEFAULT_MAX_REDIRECT = 10;

    HttpRequestFuture<String> DEFAULT_FUTURE = new HttpRequestFuture<>();
}
