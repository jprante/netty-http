package org.xbib.netty.http.server.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code ContextInfo} class holds a single context's information.
 */
public class ContextInfo {

    private final VirtualServer virtualServer;

    private final Map<String, ContextHandler> methodHandlerMap;

    public ContextInfo(VirtualServer virtualServer) {
        this.virtualServer = virtualServer;
        this.methodHandlerMap = new LinkedHashMap<>();
    }

    /**
     * Returns the map of supported HTTP methods and their corresponding handlers.
     *
     * @return the map of supported HTTP methods and their corresponding handlers
     */
    public Map<String, ContextHandler> getMethodHandlerMap() {
        return methodHandlerMap;
    }

    /**
     * Adds (or replaces) a context handler for the given HTTP methods.
     *
     * @param handler the context handler
     * @param methods the HTTP methods supported by the handler (default is "GET")
     */
    public void addHandler(ContextHandler handler, String... methods) {
        if (methods.length == 0) {
            methodHandlerMap.put("GET", handler);
            virtualServer.getMethods().add("GET");
        } else {
            for (String method : methods) {
                methodHandlerMap.put(method, handler);
                virtualServer.getMethods().add(method);
            }
        }
    }
}
