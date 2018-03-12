package org.xbib.netty.http.server.context;

import org.xbib.netty.http.server.transport.ServerRequest;
import org.xbib.netty.http.server.transport.ServerResponse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The {@code MethodContextHandler} services a context
 * by invoking a handler method on a specified object.
 * The method must have the same signature and contract as
 * {@link ContextHandler#serve}, but can have an arbitrary name.
 *
 * @see VirtualServer#addContexts(Object)
 */
public class MethodContextHandler implements ContextHandler {

    private final Method m;

    private final Object obj;

    public MethodContextHandler(Method m, Object obj) throws IllegalArgumentException {
        this.m = m;
        this.obj = obj;
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 2
                || !ServerRequest.class.isAssignableFrom(params[0])
                || !ServerResponse.class.isAssignableFrom(params[1])
                || !int.class.isAssignableFrom(m.getReturnType())) {
            throw new IllegalArgumentException("invalid method signature: " + m);
        }
    }

    @Override
    public void serve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        try {
            m.invoke(obj, serverRequest, serverResponse);
        } catch (InvocationTargetException ite) {
            throw new IOException("error: " + ite.getCause().getMessage());
        } catch (Exception e) {
            throw new IOException("error: " + e);
        }
    }
}
