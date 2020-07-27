package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.common.HttpMethod;
import org.xbib.netty.http.common.util.LimitedConcurrentHashMap;
import org.xbib.netty.http.server.api.EndpointResolver;
import org.xbib.netty.http.server.api.Filter;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.api.annotation.Endpoint;
import org.xbib.netty.http.server.endpoint.service.MethodService;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class HttpEndpointResolver implements EndpointResolver<HttpEndpoint> {

    private static final int DEFAULT_LIMIT = 1024;

    private final List<HttpEndpoint> endpoints;

    private final Filter dispatcher;

    private final Map<HttpEndpointDescriptor, List<HttpEndpoint>> endpointDescriptors;

    private HttpEndpointResolver(List<HttpEndpoint> endpoints,
                                 Filter dispatcher,
                                 Integer limit) {
        this.endpoints = endpoints;
        this.dispatcher = dispatcher;
        this.endpointDescriptors = new LimitedConcurrentHashMap<>(limit != null ? limit : DEFAULT_LIMIT);
    }

    /**
     * Find matching endpoints for a server request.
     * @return a
     */
    @Override
    public List<HttpEndpoint> matchingEndpointsFor(String path, HttpMethod method, String contentType) {
        HttpEndpointDescriptor httpEndpointDescriptor = new HttpEndpointDescriptor(path, method, contentType);
        endpointDescriptors.putIfAbsent(httpEndpointDescriptor, endpoints.stream()
                .filter(endpoint -> endpoint.matches(httpEndpointDescriptor))
                .sorted(new HttpEndpoint.EndpointPathComparator(httpEndpointDescriptor.getSortKey()))
                .collect(Collectors.toList()));
        return endpointDescriptors.get(httpEndpointDescriptor);
    }

    @Override
    public void handle(HttpEndpoint endpoint,
                       ServerRequest serverRequest,
                       ServerResponse serverResponse) throws IOException {
        endpoint.before(serverRequest, serverResponse);
        dispatcher.handle(serverRequest, serverResponse);
        endpoint.after(serverRequest, serverResponse);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<HttpEndpoint> endpoints;

        private Integer limit;

        private String prefix;

        private Filter dispatcher;

        Builder() {
            this.limit = DEFAULT_LIMIT;
            this.endpoints = new ArrayList<>();
        }

        public Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder setPrefix(String prefix) {
            Objects.requireNonNull(prefix);
            this.prefix = prefix;
            return this;
        }

        /**
         * Add endpoint under this endpoint.
         *
         * @param endpoint the endpoint
         * @return this builder
         */
        public Builder addEndpoint(HttpEndpoint endpoint) {
            Objects.requireNonNull(endpoint);
            if (prefix != null && !prefix.isEmpty()) {
                HttpEndpoint prefixedEndpoint = HttpEndpoint.builder(endpoint)
                        .setPrefix(prefix + endpoint.getPrefix())
                        .build();
                endpoints.add(prefixedEndpoint);
            } else {
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
            Objects.requireNonNull(classWithAnnotatedMethods);
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
                                .setBefore(Collections.singletonList(methodService))
                                .build());
                    }
                }
            }
            return this;
        }

        public Builder setDispatcher(Filter dispatcher) {
            Objects.requireNonNull(dispatcher);
            this.dispatcher = dispatcher;
            return this;
        }

        public HttpEndpointResolver build() {
            Objects.requireNonNull(endpoints);
            Objects.requireNonNull(dispatcher);
            Objects.requireNonNull(limit);
            if (endpoints.isEmpty()) {
                throw new IllegalArgumentException("no endpoints configured");
            }
            return new HttpEndpointResolver(endpoints, dispatcher, limit);
        }
    }
}
