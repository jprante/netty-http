import org.xbib.netty.http.server.api.ServerCertificateProvider;

module org.xbib.netty.http.bouncycastle {
    exports org.xbib.netty.http.bouncycastle;
    requires org.xbib.netty.http.server.api;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    provides ServerCertificateProvider with
            org.xbib.netty.http.bouncycastle.BouncyCastleSelfSignedCertificateProvider;
}
