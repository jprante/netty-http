package org.xbib.netty.http.server.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code Endpoint} class holds an endpoint information.
 */
public class Endpoint {

    private final NamedServer namedServer;

    private final Map<String, Handler> handlerMap;

    public Endpoint(NamedServer namedServer) {
        this.namedServer = namedServer;
        this.handlerMap = new LinkedHashMap<>();
    }

    /**
     * Returns the map of supported HTTP methods and their corresponding handlers.
     *
     * @return the map of supported HTTP methods and their corresponding handlers
     */
    public Map<String, Handler> getHandlerMap() {
        return handlerMap;
    }

    /**
     * Adds (or replaces) a handler for the given HTTP methods.
     *
     * @param handler the handler
     * @param methods the HTTP methods supported by the handler (default is "GET")
     */
    public void addHandler(Handler handler, String... methods) {
        if (methods.length == 0) {
            handlerMap.put("GET", handler);
            namedServer.getMethods().add("GET");
        } else {
            for (String method : methods) {
                handlerMap.put(method, handler);
                namedServer.getMethods().add(method);
            }
        }
    }
}
