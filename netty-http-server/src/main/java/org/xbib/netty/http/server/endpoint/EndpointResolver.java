package org.xbib.netty.http.server.endpoint;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.annotation.Endpoint;
import org.xbib.netty.http.server.endpoint.service.MethodService;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EndpointResolver {

    private static final Logger logger = Logger.getLogger(EndpointResolver.class.getName());

    private final HttpEndpoint defaultEndpoint;

    private final List<HttpEndpoint> endpoints;

    private final EndpointDispatcher endpointDispatcher;

    private final LRUCache<EndpointInfo, List<HttpEndpoint>> endpointInfos;

    private EndpointResolver(HttpEndpoint defaultEndpoint,
                             List<HttpEndpoint> endpoints,
                             EndpointDispatcher endpointDispatcher,
                             int cacheSize) {
        this.defaultEndpoint = defaultEndpoint == null ? createDefaultEndpoint() : defaultEndpoint;
        this.endpoints = endpoints;
        this.endpointDispatcher = endpointDispatcher;
        this.endpointInfos = new LRUCache<>(cacheSize);
    }

    public void resolve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        EndpointInfo endpointInfo = serverRequest.getEndpointInfo();
        endpointInfos.putIfAbsent(endpointInfo, endpoints.stream()
                .filter(endpoint -> endpoint.matches(endpointInfo))
                .sorted(new HttpEndpoint.EndpointPathComparator(endpointInfo.getPath())).collect(Collectors.toList()));
        List<HttpEndpoint> matchingEndpoints = endpointInfos.get(endpointInfo);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "endpoint info = " + endpointInfo + " matching endpoints = " + matchingEndpoints + " cache size=" + endpointInfos.size());
        }
        if (matchingEndpoints.isEmpty()) {
            if (defaultEndpoint != null) {
                defaultEndpoint.resolveUriTemplate(serverRequest);
                defaultEndpoint.executeFilters(serverRequest, serverResponse);
                if (endpointDispatcher != null) {
                    endpointDispatcher.dispatch(defaultEndpoint, serverRequest, serverResponse);
                }
            } else {
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        } else {
            for (HttpEndpoint endpoint : matchingEndpoints) {
                endpoint.resolveUriTemplate(serverRequest);
                endpoint.executeFilters(serverRequest, serverResponse);
                if (serverResponse.getStatus() != null) {
                    break;
                }
            }
            if (endpointDispatcher != null) {
                for (HttpEndpoint endpoint : matchingEndpoints) {
                    endpointDispatcher.dispatch(endpoint, serverRequest, serverResponse);
                    if (serverResponse.getStatus() != null) {
                        break;
                    }
                }
            }
        }
    }

    public Map<EndpointInfo, List<HttpEndpoint>> getEndpointInfos() {
        return endpointInfos;
    }

    protected HttpEndpoint createDefaultEndpoint() {
        return HttpEndpoint.builder()
                .setPath("/**")
                .addMethod("GET")
                .addMethod("HEAD")
                .addFilter((req, resp) -> {
                    ServerResponse.write(resp, HttpResponseStatus.NOT_FOUND,
                            "application/octet-stream","no endpoint configured");
                }).build();
    }

    /**
     * A simple LRU cache, based on a {@link LinkedHashMap}.
     *
     * @param <K> the key type parameter
     * @param <V> the vale type parameter
     */
    @SuppressWarnings("serial")
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {

        private final int cacheSize;

        LRUCache(int cacheSize) {
            super(16, 0.75f, true);
            this.cacheSize = cacheSize;
        }

        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > cacheSize;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int cacheSize;

        private String prefix;

        private HttpEndpoint defaultEndpoint;

        private List<HttpEndpoint> endpoints;

        private EndpointDispatcher endpointDispatcher;

        Builder() {
            this.cacheSize = 1024;
            this.endpoints = new ArrayList<>();
        }

        public Builder setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
            return this;
        }

        public Builder setPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder setDefaultEndpoint(HttpEndpoint endpoint) {
            this.defaultEndpoint = endpoint;
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param endpoint the endpoint
         * @return this builder
         */
        public Builder addEndpoint(HttpEndpoint endpoint) {
            if (endpoint.getPrefix().equals("/") && prefix != null && !prefix.isEmpty()) {
                HttpEndpoint thisEndpoint = HttpEndpoint.builder(endpoint).setPrefix(prefix).build();
                logger.log(Level.FINE, "adding endpoint = " + thisEndpoint);
                endpoints.add(thisEndpoint);
            } else {
                logger.log(Level.FINE, "adding endpoint = " + endpoint);
                endpoints.add(endpoint);
            }
            return this;
        }

        /**
         * Adds a service for the methods of the given object that
         * are annotated with the {@link Endpoint} annotation.
         * @param classWithAnnotatedMethods class with annotated methods
         * @return this builder
         */
        public Builder addEndpoint(Object classWithAnnotatedMethods) {
            for (Class<?> clazz = classWithAnnotatedMethods.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
                for (Method method : clazz.getDeclaredMethods()) {
                    Endpoint endpoint = method.getAnnotation(Endpoint.class);
                    if (endpoint != null) {
                        MethodService methodService = new MethodService(method, classWithAnnotatedMethods);
                        addEndpoint(HttpEndpoint.builder()
                                .setPrefix(prefix)
                                .setPath(endpoint.path())
                                .setMethods(Arrays.asList(endpoint.methods()))
                                .setContentTypes(Arrays.asList(endpoint.contentTypes()))
                                .addFilter(methodService)
                                .build());
                    }
                }
            }
            return this;
        }

        public Builder setDispatcher(EndpointDispatcher endpointDispatcher) {
            this.endpointDispatcher = endpointDispatcher;
            return this;
        }

        public EndpointResolver build() {
            return new EndpointResolver(defaultEndpoint, endpoints, endpointDispatcher, cacheSize);
        }
    }
}
