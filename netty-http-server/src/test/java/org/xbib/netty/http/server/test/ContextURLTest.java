package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.net.URL;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.endpoint.HttpEndpoint;
import org.xbib.netty.http.server.endpoint.HttpEndpointResolver;
import org.xbib.netty.http.server.transport.HttpServerRequest;

@ExtendWith(NettyHttpTestExtension.class)
public class ContextURLTest {

    @Test
    void testServerPublishURL() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);

        HttpEndpointResolver endpointResolver1 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/one").setPath("/**").build())
                .setDispatcher((endpoint, serverRequest, serverResponse) -> {})
                .build();
        HttpEndpointResolver endpointResolver2 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/two").setPath("/**").build())
                .setDispatcher((endpoint, serverRequest, serverResponse) -> {})
                .build();
        HttpEndpointResolver endpointResolver3 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/three").setPath("/**").build())
                .setDispatcher((endpoint, serverRequest, serverResponse) -> {})
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

        URL url0 = server.getContextURL();
        assertEquals("http://localhost:8008/", url0.toString());

        DefaultFullHttpRequest fullHttpRequest1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/one");
        fullHttpRequest1.headers().add("host", "domain.one:8008");
        ServerRequest serverRequest1 = new HttpServerRequest(fullHttpRequest1);
        URL url1 = server.getContextURL(serverRequest1);
        assertEquals("domain.one", url1.getHost());
        assertEquals("/one/", url1.getPath());

        DefaultFullHttpRequest fullHttpRequest2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/two");
        fullHttpRequest2.headers().add("host", "domain.two:8008");
        ServerRequest serverRequest2 = new HttpServerRequest(fullHttpRequest2);
        URL url2 = server.getContextURL(serverRequest2);
        assertEquals("domain.two", url2.getHost());
        assertEquals("/two/", url2.getPath());

        DefaultFullHttpRequest fullHttpRequest3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/three");
        fullHttpRequest3.headers().add("host", "domain.two:8008");
        ServerRequest serverRequest3 = new HttpServerRequest(fullHttpRequest3);
        URL url3 = server.getContextURL(serverRequest3);
        assertEquals("domain.two", url3.getHost());
        assertEquals("/three/", url3.getPath());
    }
}
