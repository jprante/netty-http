package org.xbib.netty.http.server.endpoint;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
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

    private final Endpoint defaultEndpoint;

    private final List<Endpoint> endpoints;

    private final EndpointDispatcher endpointDispatcher;

    private final LRUCache<Endpoint.EndpointInfo, List<Endpoint>> cache;

    private EndpointResolver(Endpoint defaultEndpoint,
                             List<Endpoint> endpoints,
                             EndpointDispatcher endpointDispatcher,
                             int cacheSize) {
        this.defaultEndpoint = defaultEndpoint == null ? createDefaultEndpoint() : defaultEndpoint;
        this.endpoints = endpoints;
        this.endpointDispatcher = endpointDispatcher;
        this.cache = new LRUCache<>(cacheSize);
    }

    public void resolve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        Endpoint.EndpointInfo endpointInfo = new Endpoint.EndpointInfo(serverRequest);
        cache.putIfAbsent(endpointInfo, endpoints.stream()
                .filter(endpoint -> endpoint.matches(endpointInfo))
                .sorted(new Endpoint.EndpointPathComparator(serverRequest.getEffectiveRequestPath())).collect(Collectors.toList()));
        List<Endpoint> matchingEndpoints = cache.get(endpointInfo);
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "matching endpoints = " + matchingEndpoints);
        }
        if (matchingEndpoints.isEmpty()) {
            if (defaultEndpoint != null) {
                defaultEndpoint.resolveUriTemplate(serverRequest);
                defaultEndpoint.executeFilters(serverRequest, serverResponse);
                if (endpointDispatcher != null) {
                    endpointDispatcher.dispatch(defaultEndpoint, serverRequest, serverResponse);
                }
            } else {
                serverResponse.write(HttpResponseStatus.NOT_IMPLEMENTED);
            }
        } else {
            for (Endpoint endpoint : matchingEndpoints) {
                endpoint.resolveUriTemplate(serverRequest);
                endpoint.executeFilters(serverRequest, serverResponse);
                if (serverResponse.getLastStatus() != null) {
                    break;
                }
            }
            if (endpointDispatcher != null) {
                for (Endpoint endpoint : matchingEndpoints) {
                    endpointDispatcher.dispatch(endpoint, serverRequest, serverResponse);
                    if (serverResponse.getLastStatus() != null) {
                        break;
                    }
                }
            }
        }
    }

    public LRUCache<Endpoint.EndpointInfo, List<Endpoint>> getCache() {
        return cache;
    }

    protected Endpoint createDefaultEndpoint() {
        return Endpoint.builder()
                .setPath("/**")
                .addMethod("GET")
                .addMethod("HEAD")
                .addFilter((req, resp) -> {
                    resp.writeError(HttpResponseStatus.NOT_FOUND,"No endpoint configured");
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
            return size() >= cacheSize;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int cacheSize;

        private String prefix;

        private Endpoint defaultEndpoint;

        private List<Endpoint> endpoints;

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

        public Builder setDefaultEndpoint(Endpoint endpoint) {
            this.defaultEndpoint = endpoint;
            return this;
        }

        /**
         *
         * @param endpoint
         * @return this builder
         */
        public Builder addEndpoint(Endpoint endpoint) {
            if (endpoint.getPrefix().equals("/") && prefix != null && !prefix.isEmpty()) {
                endpoints.add(Endpoint.builder(endpoint).setPrefix(prefix).build());
            } else {
                endpoints.add(endpoint);
            }
            return this;
        }

        /**
         * Adds a service for the methods of the given object that
         * are annotated with the {@link Context} annotation.
         */
        public Builder addEndpoint(Object classWithAnnotatedMethods) {
            for (Class<?> clazz = classWithAnnotatedMethods.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
                for (Method method : clazz.getDeclaredMethods()) {
                    Context context = method.getAnnotation(Context.class);
                    if (context != null) {
                        addEndpoint(Endpoint.builder()
                                .setPrefix(prefix)
                                .setPath(context.value())
                                .setMethods(Arrays.asList(context.methods()))
                                .addFilter(new MethodService(method, classWithAnnotatedMethods))
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
