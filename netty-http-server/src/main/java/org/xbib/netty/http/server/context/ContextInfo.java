package org.xbib.netty.http.server.context;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code ContextInfo} class holds a single context's information.
 */
public class ContextInfo {

    private final Map<String, ContextHandler> handlers = new HashMap<>(2);

    private final VirtualServer virtualServer;

    public ContextInfo(VirtualServer virtualServer) {
        this.virtualServer = virtualServer;
    }

    /**
     * Returns the map of supported HTTP methods and their corresponding handlers.
     *
     * @return the map of supported HTTP methods and their corresponding handlers
     */
    public Map<String, ContextHandler> getHandlers() {
        return handlers;
    }

    /**
     * Adds (or replaces) a context handler for the given HTTP methods.
     *
     * @param handler the context handler
     * @param methods the HTTP methods supported by the handler (default is "GET")
     */
    public void addHandler(ContextHandler handler, String... methods) {
        if (methods.length == 0) {
            methods = new String[]{"GET"};
        }
        for (String method : methods) {
            handlers.put(method, handler);
            virtualServer.getMethods().add(method);
        }
    }
}
