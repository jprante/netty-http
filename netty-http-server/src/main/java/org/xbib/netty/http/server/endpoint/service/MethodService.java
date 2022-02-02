package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.api.Filter;
import org.xbib.netty.http.server.api.FilterConfig;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * The {@code MethodHandler} invokes a handler method on a specified object.
 * The method must have the same signature and contract as
 * {@link Filter#handle}, but can have an arbitrary name.
 */
public class MethodService implements Filter {

    private final Method m;

    private final Object obj;

    public MethodService(Method m, Object obj) throws IllegalArgumentException {
        this.m = m;
        this.obj = obj;
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 2 ||
                !ServerRequest.class.isAssignableFrom(params[0]) ||
                !ServerResponse.class.isAssignableFrom(params[1]) ||
                !int.class.isAssignableFrom(m.getReturnType())) {
            throw new IllegalArgumentException("invalid method signature: " + m);
        }
    }

    public void initialize(FilterConfig filterConfig) {
    }

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        try {
            m.invoke(obj, serverRequest, serverResponse);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
