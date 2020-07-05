module org.xbib.netty.http.bouncycastle {
    exports org.xbib.netty.http.bouncycastle;
    requires org.xbib.netty.http.server.api;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    provides org.xbib.netty.http.server.api.security.ServerCertificateProvider with
            org.xbib.netty.http.bouncycastle.BouncyCastleSelfSignedCertificateProvider;
}
