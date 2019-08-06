package org.xbib.netty.http.common.security;

public enum Algo {
    MD5("MD5", "md5"),
    SHA("SHA","sha"),
    SHA256("SHA-256","sha256"),
    SHA512("SHA-512", "sha512"),
    SSHA("SHA1", "ssha"),
    SSHA256("SHA-256", "ssha"),
    SSHA512("SHA-512", "ssha");

    String algo;

    String prefix;

    Algo(String algo, String prefix) {
        this.algo = algo;
        this.prefix = prefix;
    }
}
