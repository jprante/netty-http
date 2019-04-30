package org.xbib.netty.http.server.endpoint;

public class NamedEndpoint {

    private final String name;

    private final Endpoint endpoint;

    NamedEndpoint(String name, Endpoint endpoint) {
        this.name = name;
        this.endpoint = endpoint;
    }

    public String getName() {
        return name;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}
