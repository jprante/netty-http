package org.xbib.netty.http.common.cookie;

import org.xbib.netty.http.common.util.LRUCache;

@SuppressWarnings("serial")
public class CookieBox extends LRUCache<Cookie, Boolean> {

    public CookieBox(int cacheSize) {
        super(cacheSize);
    }
}
