package org.xbib.netty.http.client.listener;

import org.xbib.netty.http.common.cookie.Cookie;

@FunctionalInterface
public interface CookieListener {

    void onCookie(Cookie cookie);
}
