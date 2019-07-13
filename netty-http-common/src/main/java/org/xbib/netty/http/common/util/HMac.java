package org.xbib.netty.http.common.util;

public enum HMac {
    HMAC_SHA1("HMacSHA1"),
    HMAC_SHA256("HMacSHA256");

    String algo;

    HMac(String algo) {
        this.algo = algo;
    }
}
