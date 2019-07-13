package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The {@code MethodHandler} invokes g a handler method on a specified object.
 * The method must have the same signature and contract as
 * {@link Service#handle}, but can have an arbitrary name.
 */
public class MethodService implements Service {

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

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        try {
            m.invoke(obj, serverRequest, serverResponse);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
