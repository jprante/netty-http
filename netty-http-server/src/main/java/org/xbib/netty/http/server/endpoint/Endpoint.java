package org.xbib.netty.http.server.endpoint;

import org.xbib.net.QueryParameters;
import org.xbib.net.path.PathMatcher;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.service.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public class Endpoint {

    private static final PathMatcher pathMatcher = new PathMatcher();

    public  static final List<String> DEFAULT_METHODS = Arrays.asList("GET", "HEAD");

    private final String prefix;

    private final String path;

    private final List<String> methods;

    private final List<String> contentTypes;

    private final List<Service> filters;

    private Endpoint(String prefix, String path,
             List<String> methods, List<String> contentTypes, List<Service> filters) {
        this.prefix = prefix;
        this.path = path == null || path.isEmpty() ?
                prefix + "/**" : path.startsWith("/") ? prefix + path : prefix + "/" + path;
        this.methods = methods;
        this.contentTypes = contentTypes;
        this.filters = filters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Endpoint endpoint) {
        return new Builder()
                .setPrefix(endpoint.prefix)
                .setPath(endpoint.path)
                .setMethods(endpoint.methods)
                .setContentTypes(endpoint.contentTypes)
                .setFilters(endpoint.filters);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getPath() {
        return path;
    }

    public boolean matches(EndpointInfo info) {
        return pathMatcher.match(path, info.path) &&
                (methods == null || methods.isEmpty() || (methods.contains(info.method))) &&
                (contentTypes == null || contentTypes.isEmpty() || info.contentType == null ||
                contentTypes.stream().anyMatch(info.contentType::startsWith));
    }

    public void resolveUriTemplate(ServerRequest serverRequest) {
        if (pathMatcher.match(path, serverRequest.getEffectiveRequestPath())) {
            QueryParameters queryParameters = pathMatcher.extractUriTemplateVariables(path, serverRequest.getEffectiveRequestPath());
            Map<String, String> map = new LinkedHashMap<>();
            for (QueryParameters.Pair<String, String> pair : queryParameters) {
                map.put(pair.getFirst(), pair.getSecond());
            }
            serverRequest.setRawParameters(map);
        }
    }

    public void executeFilters(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        serverRequest.setContext(pathMatcher.tokenizePath(getPrefix()));
        for (Service service : filters) {
            service.handle(serverRequest, serverResponse);
            if (serverResponse.getLastStatus() != null) {
                break;
            }
        }
    }

    @Override
    public String toString() {
        return path + "_" + methods + "_" + contentTypes + " --> " + filters;
    }

    public static class EndpointInfo implements Comparable<EndpointInfo> {

        private final String path;

        private final String method;

        private final String contentType;

        public EndpointInfo(ServerRequest serverRequest) {
            this.path = serverRequest.getEffectiveRequestPath();
            this.method = serverRequest.getRequest().method().name();
            this.contentType = serverRequest.getRequest().headers().get(CONTENT_TYPE);
        }

        @Override
        public String toString() {
            return path + "_" + method + "_" + contentType;
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EndpointInfo && toString().equals(o.toString());
        }

        @Override
        public int compareTo(EndpointInfo o) {
            return toString().compareTo(o.toString());
        }
    }

    static class EndpointPathComparator implements Comparator<Endpoint> {

        private final Comparator<String> pathComparator;

        EndpointPathComparator(String path) {
            this.pathComparator = pathMatcher.getPatternComparator(path);
        }

        @Override
        public int compare(Endpoint endpoint1, Endpoint endpoint2) {
            return pathComparator.compare(endpoint1.path, endpoint2.path);
        }
    }

    public static class Builder {

        private String prefix;

        private String path;

        private List<String> methods;

        private List<String> contentTypes;

        private List<Service> filters;

        Builder() {
            this.prefix = "/";
            this.path = "/**";
            this.methods = new ArrayList<>();
            this.contentTypes = new ArrayList<>();
            this.filters = new ArrayList<>();
        }

        public Builder setPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setMethods(List<String> methods) {
            this.methods = methods;
            return this;
        }

        public Builder addMethod(String method) {
            methods.add(method);
            return this;
        }

        public Builder setContentTypes(List<String> contentTypes) {
            this.contentTypes = contentTypes;
            return this;
        }

        public Builder addContentType(String contentType) {
            this.contentTypes.add(contentType);
            return this;
        }

        public Builder setFilters(List<Service> filters) {
            this.filters = filters;
            return this;
        }

        public Builder addFilter(Service filter) {
            this.filters.add(filter);
            return this;
        }

        public Endpoint build() {
            if (methods.isEmpty()) {
                methods = DEFAULT_METHODS;
            }
            return new Endpoint(prefix, path, methods, contentTypes, filters);
        }
    }
}
