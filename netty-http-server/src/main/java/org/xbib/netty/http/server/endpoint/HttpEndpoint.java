package org.xbib.netty.http.server.endpoint;

import org.xbib.datastructures.common.Pair;
import org.xbib.net.Parameter;
import org.xbib.net.PathNormalizer;
import org.xbib.net.path.simple.Path;
import org.xbib.net.path.simple.PathComparator;
import org.xbib.net.path.simple.PathMatcher;
import org.xbib.netty.http.common.HttpMethod;
import org.xbib.netty.http.server.api.Domain;
import org.xbib.netty.http.server.api.Endpoint;
import org.xbib.netty.http.server.api.EndpointResolver;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.api.Filter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HttpEndpoint implements Endpoint<HttpEndpointDescriptor> {

    public static final EnumSet<HttpMethod> DEFAULT_METHODS =
            EnumSet.of(HttpMethod.GET, HttpMethod.HEAD);

    private static final PathMatcher pathMatcher = new PathMatcher();

    private final String prefix;

    private final String path;

    private final EnumSet<HttpMethod> methods;

    private final List<String> contentTypes;

    private final List<Filter> beforeFilters;

    private final List<Filter> afterFilters;

    private HttpEndpoint(String prefix,
                         String path,
                         EnumSet<HttpMethod> methods,
                         List<String> contentTypes,
                         List<Filter> beforeFilters,
                         List<Filter> afterFilters) {
        this.prefix = PathNormalizer.normalize(prefix);
        this.path = PathNormalizer.normalize(path);
        this.methods = methods;
        this.contentTypes = contentTypes;
        this.beforeFilters = beforeFilters;
        this.afterFilters = afterFilters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(HttpEndpoint endpoint) {
        return new Builder()
                .setPrefix(endpoint.prefix)
                .setPath(endpoint.path)
                .setMethods(endpoint.methods)
                .setContentTypes(endpoint.contentTypes)
                .setBefore(endpoint.beforeFilters)
                .setAfter(endpoint.afterFilters);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean matches(HttpEndpointDescriptor httpEndpointDescriptor) {
        return pathMatcher.match(prefix + path, httpEndpointDescriptor.getPath()) &&
                (methods == null || methods.isEmpty() || (methods.contains(httpEndpointDescriptor.getMethod()))) &&
                (contentTypes == null || contentTypes.isEmpty() || httpEndpointDescriptor.getContentType() == null ||
                contentTypes.stream().anyMatch(httpEndpointDescriptor.getContentType()::startsWith));
    }

    @Override
    public ServerRequest resolveRequest(ServerRequest.Builder serverRequestBuilder,
                                        Domain<? extends EndpointResolver<? extends Endpoint<?>>> domain,
                                        EndpointResolver<? extends Endpoint<?>> endpointResolver) {
        List<String> context = pathMatcher.tokenize(getPrefix());
        serverRequestBuilder.setDomain(domain)
                .setEndpointResolver(endpointResolver)
                .setEndpoint((this))
                .setContext(context);
        String pattern = path;
        String effectiveRequestPath = serverRequestBuilder.getEffectiveRequestPath();
        if (pathMatcher.match(pattern, effectiveRequestPath)) {
            Parameter queryParameters = pathMatcher.extractUriTemplateVariables(pattern, effectiveRequestPath);
            for (Pair<String, Object> pair : queryParameters) {
                serverRequestBuilder.addPathParameter(pair.getKey(), pair.getValue().toString());
            }
        }
        return serverRequestBuilder.build();
    }

    @Override
    public void before(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        if (serverResponse != null) {
            for (Filter filter : beforeFilters) {
                filter.handle(serverRequest, serverResponse);
            }
        }
    }

    @Override
    public void after(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        if (serverResponse != null) {
            for (Filter filter : afterFilters) {
                filter.handle(serverRequest, serverResponse);
            }
        }
    }

    @Override
    public String toString() {
        return "Endpoint[prefix=" + prefix + ",path=" + path +
                ",methods=" + methods +
                ",contentTypes=" + contentTypes +
                ",before=" + beforeFilters +
                ",after=" + afterFilters +
                "]";
    }

    static class EndpointPathComparator implements Comparator<HttpEndpoint> {

        private final PathComparator pathComparator;

        EndpointPathComparator(String path) {
            this.pathComparator = pathMatcher.getPatternComparator(path);
        }

        @Override
        public int compare(HttpEndpoint endpoint1, HttpEndpoint endpoint2) {
            return pathComparator.compare(Path.of(endpoint1.path), Path.of(endpoint2.path));
        }
    }

    public static class Builder {

        private String prefix;

        private String path;

        private EnumSet<HttpMethod> methods;

        private List<String> contentTypes;

        private List<Filter> beforeFilters;

        private List<Filter> afterFilters;

        Builder() {
            this.prefix = "/";
            this.path = "/**";
            this.methods = DEFAULT_METHODS;
            this.contentTypes = new ArrayList<>();
            this.beforeFilters = new ArrayList<>();
            this.afterFilters = new ArrayList<>();
        }

        public Builder setPrefix(String prefix) {
            Objects.requireNonNull(prefix);
            this.prefix = prefix;
            return this;
        }

        public Builder setPath(String path) {
            Objects.requireNonNull(path);
            this.path = path;
            return this;
        }

        public Builder setMethods(EnumSet<HttpMethod> methods) {
            Objects.requireNonNull(methods);
            this.methods = methods;
            return this;
        }

        public Builder setMethods(List<String> methods) {
            Objects.requireNonNull(methods);
            this.methods = methods.stream()
                    .map(HttpMethod::valueOf)
                    .collect(Collectors.toCollection(() ->  EnumSet.noneOf(HttpMethod.class)));
            return this;
        }

        public Builder setContentTypes(List<String> contentTypes) {
            Objects.requireNonNull(contentTypes);
            this.contentTypes = contentTypes;
            return this;
        }

        public Builder addContentType(String contentType) {
            Objects.requireNonNull(contentType);
            this.contentTypes.add(contentType);
            return this;
        }

        public Builder setBefore(List<Filter> filters) {
            Objects.requireNonNull(filters);
            this.beforeFilters = filters;
            return this;
        }

        public Builder setAfter(List<Filter> filters) {
            Objects.requireNonNull(filters);
            this.afterFilters = filters;
            return this;
        }

        public HttpEndpoint build() {
            return new HttpEndpoint(prefix, path, methods, contentTypes,
                    beforeFilters, afterFilters);
        }
    }
}
