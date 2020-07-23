package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.common.util.LimitedConcurrentHashMap;
import org.xbib.netty.http.server.api.EndpointDispatcher;
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

public class HttpEndpointResolver {

    private static final int DEFAULT_LIMIT = 1024;

    private final List<HttpEndpoint> endpoints;

    private final EndpointDispatcher<HttpEndpoint> endpointDispatcher;

    private final Map<HttpEndpointDescriptor, List<HttpEndpoint>> endpointDescriptors;

    private HttpEndpointResolver(List<HttpEndpoint> endpoints,
                                 EndpointDispatcher<HttpEndpoint> endpointDispatcher,
                                 int limit) {
        Objects.requireNonNull(endpointDispatcher);
        this.endpoints = endpoints;
        this.endpointDispatcher = endpointDispatcher;
        this.endpointDescriptors = new LimitedConcurrentHashMap<>(limit);
    }

    /**
     * Find matching endpoints for a server request.
     * @param serverRequest the server request
     * @return a
     */
    public List<HttpEndpoint> matchingEndpointsFor(ServerRequest serverRequest) {
        HttpEndpointDescriptor httpEndpointDescriptor = new HttpEndpointDescriptor(serverRequest);
        endpointDescriptors.putIfAbsent(httpEndpointDescriptor, endpoints.stream()
                .filter(endpoint -> endpoint.matches(httpEndpointDescriptor))
                .sorted(new HttpEndpoint.EndpointPathComparator(httpEndpointDescriptor.getSortKey()))
                .collect(Collectors.toList()));
        return endpointDescriptors.get(httpEndpointDescriptor);
    }

    public void handle(List<HttpEndpoint> matchingEndpoints,
                       ServerRequest serverRequest,
                       ServerResponse serverResponse,
                       boolean dispatch) throws IOException {
        Objects.requireNonNull(matchingEndpoints);
        for (HttpEndpoint endpoint : matchingEndpoints) {
            endpoint.resolveUriTemplate(serverRequest);
            endpoint.before(serverRequest, serverResponse);
            if (dispatch) {
                endpointDispatcher.dispatch(endpoint, serverRequest, serverResponse);
                endpoint.after(serverRequest, serverResponse);
                if (serverResponse != null && serverResponse.getStatus() != null) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<HttpEndpoint> endpoints;

        private int limit;

        private String prefix;

        private EndpointDispatcher<HttpEndpoint> endpointDispatcher;

        Builder() {
            this.limit = DEFAULT_LIMIT;
            this.endpoints = new ArrayList<>();
        }

        public Builder setLimit(int limit) {
            this.limit = limit > 0 ? limit < 1024 * DEFAULT_LIMIT ? limit : DEFAULT_LIMIT : DEFAULT_LIMIT;
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

        public Builder setDispatcher(EndpointDispatcher<HttpEndpoint> endpointDispatcher) {
            Objects.requireNonNull(endpointDispatcher);
            this.endpointDispatcher = endpointDispatcher;
            return this;
        }

        public HttpEndpointResolver build() {
            if (endpoints.isEmpty()) {
                throw new IllegalArgumentException("no endpoints configured");
            }
            return new HttpEndpointResolver(endpoints, endpointDispatcher, limit);
        }
    }
}
