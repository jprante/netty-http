package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.endpoint.HttpEndpoint;
import org.xbib.netty.http.server.endpoint.HttpEndpointResolver;
import org.xbib.netty.http.server.HttpServerRequest;

@ExtendWith(NettyHttpTestExtension.class)
public class ContextURLTest {

    @Test
    void testServerPublishURL() {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);

        HttpEndpointResolver endpointResolver1 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/one").setPath("/**").build())
                .setDispatcher((serverRequest, serverResponse) -> {})
                .build();
        HttpEndpointResolver endpointResolver2 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/two").setPath("/**").build())
                .setDispatcher((serverRequest, serverResponse) -> {})
                .build();
        HttpEndpointResolver endpointResolver3 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/three").setPath("/**").build())
                .setDispatcher((serverRequest, serverResponse) -> {})
                .build();

        HttpServerDomain one = HttpServerDomain.builder(httpAddress, "domain.one:8008")
                .addEndpointResolver(endpointResolver1)
                .build();
        HttpServerDomain two = HttpServerDomain.builder(one)
                .setServerName("domain.two:8008")
                .addEndpointResolver(endpointResolver2)
                .addEndpointResolver(endpointResolver3)
                .build();

        Server server = Server.builder(one)
                .addDomain(two)
                .build();

        DefaultFullHttpRequest fullHttpRequest1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/one");
        fullHttpRequest1.headers().add("host", "domain.one:8008");
        ServerRequest serverRequest1 = HttpServerRequest.builder()
                .setHttpRequest(fullHttpRequest1)
                .applyTo(server);
        String contextPath1 = serverRequest1.getContextPath();
        assertEquals("/one", contextPath1);
        URL url1 = serverRequest1.getContextURL();
        assertNotNull(url1);
        assertEquals("domain.one", url1.getHost());
        assertEquals("/one/", url1.getPath());

        DefaultFullHttpRequest fullHttpRequest2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/two");
        fullHttpRequest2.headers().add("host", "domain.two:8008");
        ServerRequest serverRequest2 = HttpServerRequest.builder().setHttpRequest(fullHttpRequest2).applyTo(server);
        String contextPath2 = serverRequest2.getContextPath();
        assertEquals("/two", contextPath2);
        URL url2 = serverRequest2.getContextURL();
        assertNotNull(url2);
        assertEquals("domain.two", url2.getHost());
        assertEquals("/two/", url2.getPath());

        DefaultFullHttpRequest fullHttpRequest3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/three");
        fullHttpRequest3.headers().add("host", "domain.two:8008");
        ServerRequest serverRequest3 = HttpServerRequest.builder().setHttpRequest(fullHttpRequest3).applyTo(server);
        URL url3 = serverRequest3.getContextURL();
        assertEquals("domain.two", url3.getHost());
        assertEquals("/three/", url3.getPath());
    }
}
