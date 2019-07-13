package org.xbib.netty.http.common.cookie;

import org.xbib.net.PercentDecoder;
import org.xbib.net.PercentEncoder;
import org.xbib.net.PercentEncoders;
import org.xbib.netty.http.common.util.Codec;
import org.xbib.netty.http.common.util.CryptUtils;
import org.xbib.netty.http.common.util.HMac;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class Payload {

    private static final PercentEncoder PERCENT_ENCODER = PercentEncoders.getCookieEncoder(StandardCharsets.UTF_8);

    private static final PercentDecoder PERCENT_DECODER = new PercentDecoder(StandardCharsets.UTF_8.newDecoder());

    private final Codec codec;

    private final HMac hmac;

    private final String publicValue;

    private final String privateValue;

    private final String secret;

    public Payload(Codec codec, HMac hmac, String publicValue, String privateValue, String secret) {
        this.codec = codec;
        this.hmac = hmac;
        this.publicValue = publicValue;
        this.privateValue = privateValue;
        this.secret = secret;
    }

    public Payload(Codec codec, HMac hmac, String rawValue, String secret)
            throws MalformedInputException, UnmappableCharacterException, InvalidKeyException,
            NoSuchAlgorithmException, SignatureException {
        this.codec = codec;
        this.hmac = hmac;
        String[] s = PERCENT_DECODER.decode(rawValue).split(":", 3);
        if (s.length != 3) {
            throw new IllegalStateException();
        }
        this.publicValue = s[0];
        this.privateValue = s[1];
        this.secret = secret;
        if (!s[2].equals(CryptUtils.hmac(codec, privateValue, secret, hmac))) {
            throw new SignatureException("HMAC signature does not match");
        }
    }

    public String getPublicValue() {
        return publicValue;
    }

    public String getPrivateValue() {
        return privateValue;
    }

    public String getSecret() {
        return secret;
    }

    public String toString() {
        try {
            return PERCENT_ENCODER.encode(String.join(":", publicValue, privateValue,
                    CryptUtils.hmac(codec, privateValue, secret, hmac)));
        } catch (NoSuchAlgorithmException | InvalidKeyException | MalformedInputException | UnmappableCharacterException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
