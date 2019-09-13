package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.common.util.LimitedConcurrentHashMap;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.annotation.Endpoint;
import org.xbib.netty.http.server.endpoint.service.MethodService;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HttpEndpointResolver {

    private static final Logger logger = Logger.getLogger(HttpEndpointResolver.class.getName());

    private static final int DEFAULT_LIMIT = 1024;

    private final List<HttpEndpoint> endpoints;

    private final EndpointDispatcher endpointDispatcher;

    private final Map<HttpEndpointDescriptor, List<HttpEndpoint>> endpointDescriptors;

    private HttpEndpointResolver(List<HttpEndpoint> endpoints,
                                 EndpointDispatcher endpointDispatcher,
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
        HttpEndpointDescriptor httpEndpointDescriptor = serverRequest.getEndpointDescriptor();
        endpointDescriptors.putIfAbsent(httpEndpointDescriptor, endpoints.stream()
                .filter(endpoint -> endpoint.matches(httpEndpointDescriptor))
                .sorted(new HttpEndpoint.EndpointPathComparator(httpEndpointDescriptor.getPath()))
                .collect(Collectors.toList()));
        return endpointDescriptors.get(httpEndpointDescriptor);
    }

    public void handle(List<HttpEndpoint> matchingEndpoints,
                       ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        Objects.requireNonNull(matchingEndpoints);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, () ->
                    "endpoint = " + serverRequest.getEndpointDescriptor() +
                            " matching endpoints = " + matchingEndpoints.size() + " --> " + matchingEndpoints);
        }
        for (HttpEndpoint endpoint : matchingEndpoints) {
            endpoint.resolveUriTemplate(serverRequest);
            endpoint.handle(serverRequest, serverResponse);
            endpointDispatcher.dispatch(endpoint, serverRequest, serverResponse);
            if (serverResponse.getStatus() != null) {
                logger.log(Level.FINEST, () -> "endpoint " + endpoint + " status = " + serverResponse.getStatus());
                break;
            }
        }
    }

    public Map<HttpEndpointDescriptor, List<HttpEndpoint>> getEndpointDescriptors() {
        return endpointDescriptors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int limit;

        private String prefix;

        private List<HttpEndpoint> endpoints;

        private EndpointDispatcher endpointDispatcher;

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
         * Add endpoint.
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
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, () -> "prefix " + prefix + ": adding endpoint = " + prefixedEndpoint);
                }
                endpoints.add(prefixedEndpoint);
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, () -> "adding endpoint = " + endpoint);
                }
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
                                .addFilter(methodService)
                                .build());
                    }
                }
            }
            return this;
        }

        public Builder setDispatcher(EndpointDispatcher endpointDispatcher) {
            Objects.requireNonNull(endpointDispatcher);
            this.endpointDispatcher = endpointDispatcher;
            return this;
        }

        public HttpEndpointResolver build() {
            return new HttpEndpointResolver(endpoints, endpointDispatcher, limit);
        }
    }
}
