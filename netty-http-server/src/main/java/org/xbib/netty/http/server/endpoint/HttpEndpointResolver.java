package org.xbib.netty.http.server.endpoint;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.common.util.LimitedMap;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HttpEndpointResolver {

    private static final Logger logger = Logger.getLogger(HttpEndpointResolver.class.getName());

    private final HttpEndpoint defaultEndpoint;

    private final List<HttpEndpoint> endpoints;

    private final EndpointDispatcher endpointDispatcher;

    private final LimitedMap<HttpEndpointDescriptor, List<HttpEndpoint>> endpointDescriptors;

    private HttpEndpointResolver(HttpEndpoint defaultEndpoint,
                                 List<HttpEndpoint> endpoints,
                                 EndpointDispatcher endpointDispatcher,
                                 int limit) {
        this.defaultEndpoint = defaultEndpoint == null ? createDefaultEndpoint() : defaultEndpoint;
        this.endpoints = endpoints;
        this.endpointDispatcher = endpointDispatcher;
        this.endpointDescriptors = new LimitedMap<>(limit);
    }

    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        HttpEndpointDescriptor httpEndpointDescriptor = serverRequest.getEndpointDescriptor();
        endpointDescriptors.putIfAbsent(httpEndpointDescriptor, endpoints.stream()
                .filter(endpoint -> endpoint.matches(httpEndpointDescriptor))
                .sorted(new HttpEndpoint.EndpointPathComparator(httpEndpointDescriptor.getPath()))
                .collect(Collectors.toList()));
        List<HttpEndpoint> matchingEndpoints = endpointDescriptors.get(httpEndpointDescriptor);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, () -> "endpoint = " + httpEndpointDescriptor +
                    " matching endpoints = " + matchingEndpoints);
        }
        if (matchingEndpoints.isEmpty()) {
            if (defaultEndpoint != null) {
                defaultEndpoint.resolveUriTemplate(serverRequest);
                defaultEndpoint.handle(serverRequest, serverResponse);
                if (endpointDispatcher != null) {
                    endpointDispatcher.dispatch(defaultEndpoint, serverRequest, serverResponse);
                }
            } else {
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        } else {
            for (HttpEndpoint endpoint : matchingEndpoints) {
                endpoint.resolveUriTemplate(serverRequest);
                endpoint.handle(serverRequest, serverResponse);
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

    public Map<HttpEndpointDescriptor, List<HttpEndpoint>> getEndpointDescriptors() {
        return endpointDescriptors;
    }

    private HttpEndpoint createDefaultEndpoint() {
        return HttpEndpoint.builder()
                .setPath("/**")
                .addMethod("GET")
                .addMethod("HEAD")
                .addFilter((req, resp) -> {
                    ServerResponse.write(resp, HttpResponseStatus.NOT_FOUND,
                            "application/octet-stream","no endpoint configured");
                }).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int limit;

        private String prefix;

        private HttpEndpoint defaultEndpoint;

        private List<HttpEndpoint> endpoints;

        private EndpointDispatcher endpointDispatcher;

        Builder() {
            this.limit = 1024;
            this.endpoints = new ArrayList<>();
        }

        public Builder setLimit(int limit) {
            this.limit = limit;
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

        public HttpEndpointResolver build() {
            return new HttpEndpointResolver(defaultEndpoint, endpoints, endpointDispatcher, limit);
        }
    }
}
