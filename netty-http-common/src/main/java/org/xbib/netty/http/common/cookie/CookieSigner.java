package org.xbib.netty.http.common.cookie;

import org.xbib.net.PercentDecoder;
import org.xbib.net.PercentEncoder;
import org.xbib.net.PercentEncoders;
import org.xbib.netty.http.common.security.Codec;
import org.xbib.netty.http.common.security.CryptUtil;
import org.xbib.netty.http.common.security.HMac;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class CookieSigner {

    private String signature;

    private final String publicValue;

    private final String privateValue;

    private String cookieValue;

    /**
     * Construct cookie for signing.
     *
     * @param charset the character set
     * @param hmac the HMAC code
     * @param codec the codec for the private value
     * @param privateValue the private value
     * @param publicValue the public value
     * @param secret the secret
     * @throws MalformedInputException if signing cookie fails
     * @throws UnmappableCharacterException if signing cookie fails
     * @throws NoSuchAlgorithmException if signing cookie fails
     * @throws InvalidKeyException if signing cookie fails
     */
    private CookieSigner(Charset charset, HMac hmac, Codec codec, String privateValue, String publicValue, String secret)
            throws MalformedInputException, UnmappableCharacterException, NoSuchAlgorithmException, InvalidKeyException {
        PercentEncoder percentEncoder = PercentEncoders.getCookieEncoder(charset);
        this.privateValue = privateValue;
        this.publicValue = publicValue;
        this.signature = CryptUtil.hmac(charset, hmac, codec, privateValue, secret);
        this.cookieValue = percentEncoder.encode(String.join(":", publicValue, privateValue, signature));
    }

    /**
     * Parse signed cookie value.
     *
     * @param charset the character set
     * @param hmac the HMAC code
     * @param codec the codec for the private value
     * @param rawValue the raw value for parsing
     * @param secret the secret
     * @throws MalformedInputException if parsing failed
     * @throws UnmappableCharacterException if parsing failed
     * @throws NoSuchAlgorithmException if parsing failed
     * @throws InvalidKeyException if parsing failed
     * @throws SignatureException if signature is invalid
     */
    private CookieSigner(Charset charset, HMac hmac, Codec codec, String rawValue, String secret)
            throws MalformedInputException, UnmappableCharacterException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException {
        PercentDecoder persentDecoder = new PercentDecoder(charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        );
        String[] s = persentDecoder.decode(rawValue).split(":", 3);
        if (s.length != 3) {
            throw new IllegalStateException("unable to find three colon-separated components in cookie value");
        }
        this.signature = CryptUtil.hmac(charset, hmac, codec, s[1], secret);
        if (!s[2].equals(signature)) {
            throw new SignatureException("HMAC signature does not match");
        }
        this.publicValue = s[0];
        this.privateValue = s[1];
        this.cookieValue = rawValue;
    }

    public String getPublicValue() {
        return publicValue;
    }

    public String getPrivateValue() {
        return privateValue;
    }

    public String getCookieValue() {
        return cookieValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Charset charset;

        private HMac hmac;

        private Codec codec;

        private String privateValue;

        private String publicValue;

        private String secret;

        private String rawValue;

        public Builder() {
            this.charset = StandardCharsets.UTF_8;
            this.hmac = HMac.HMAC_SHA1;
            this.codec = Codec.BASE64;
        }

        public Builder withCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder withHMac(HMac hmac) {
            this.hmac = hmac;
            return this;
        }

        public Builder withCodec(Codec codec) {
            this.codec = codec;
            return this;
        }

        public Builder withPrivateValue(String privateValue) {
            this.privateValue = privateValue;
            return this;
        }

        public Builder withPublicValue(String publicValue) {
            this.publicValue = publicValue;
            return this;
        }

        public Builder withSecret(String secret) {
            this.secret = secret;
            return this;
        }

        public Builder withRawValue(String rawValue) {
            this.rawValue = rawValue;
            return this;
        }

        public CookieSigner build() {
            try {
                return rawValue != null ?
                        new CookieSigner(charset, hmac, codec, rawValue, secret) :
                        new CookieSigner(charset, hmac, codec, privateValue, publicValue, secret);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
