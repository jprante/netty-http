package org.xbib.netty.http.client.handler.http1;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.handler.TrafficLoggingHandler;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.util.Collections;

public class HttpChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final HttpResponseHandler httpResponseHandler;

    public HttpChannelInitializer(ClientConfig clientConfig, HttpAddress httpAddress, HttpResponseHandler httpResponseHandler) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.httpResponseHandler = httpResponseHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new TrafficLoggingHandler());
        if (httpAddress.isSecure()) {
            configureEncryptedHttp1(ch);
        } else {
            configureCleartextHttp1(ch);
        }
    }

    private void configureEncryptedHttp1(SocketChannel ch)  {
        ChannelPipeline pipeline = ch.pipeline();
        try {
            SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                    .sslProvider(clientConfig.getSslProvider())
                    .keyManager(clientConfig.getKeyCertChainInputStream(), clientConfig.getKeyInputStream(),
                            clientConfig.getKeyPassword())
                    .ciphers(clientConfig.getCiphers(), clientConfig.getCipherSuiteFilter())
                    .trustManager(clientConfig.getTrustManagerFactory());
            SslHandler sslHandler = sslContextBuilder.build().newHandler(ch.alloc());
            SSLEngine engine = sslHandler.engine();
            if (clientConfig.isServerNameIdentification()) {
                String fullQualifiedHostname = httpAddress.getInetSocketAddress().getHostName();
                SSLParameters params = engine.getSSLParameters();
                params.setServerNames(Collections.singletonList(new SNIHostName(fullQualifiedHostname)));
                engine.setSSLParameters(params);
            }
            pipeline.addLast(sslHandler);
            switch (clientConfig.getClientAuthMode()) {
                case NEED:
                    engine.setNeedClientAuth(true);
                    break;
                case WANT:
                    engine.setWantClientAuth(true);
                    break;
                default:
                    break;
            }
        } catch (SSLException e) {
            throw new IllegalStateException("unable to configure SSL: " + e.getMessage(), e);
        }
        configureCleartextHttp1(ch);
    }

    private void configureCleartextHttp1(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpClientCodec(clientConfig.getMaxInitialLineLength(),
                 clientConfig.getMaxHeadersSize(), clientConfig.getMaxChunkSize()));
        if (clientConfig.isEnableGzip()) {
            pipeline.addLast(new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator = new HttpObjectAggregator(clientConfig.getMaxContentLength(),
                false);
        httpObjectAggregator.setMaxCumulationBufferComponents(clientConfig.getMaxCompositeBufferComponents());
        pipeline.addLast(httpObjectAggregator);
        pipeline.addLast(httpResponseHandler);
    }
}
