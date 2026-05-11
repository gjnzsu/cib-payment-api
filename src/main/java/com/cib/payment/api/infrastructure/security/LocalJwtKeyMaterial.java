package com.cib.payment.api.infrastructure.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class LocalJwtKeyMaterial {
    private static final String PRIVATE_KEY = """
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDgp11047QAmR6g
            KdcAfDg87pzE/RtqUIlHR35j1d1H8aVZEsgcQSlsgeF620kLtIQDCfX7Gr7DDwuo
            OuOCBb27dB85xNUpUiNlYSgh7SPw0+xwhYq8CqUB98aznbpchW/P3QZuLVW1N4cF
            jhZTjsysR8bgeMKOZvnZYbuLqnXEnfwa5HTHquNGSMMIaIRjPQYJEuz5rM/28KUs
            Zio+nxGQgtbKZvdh/I38gKHMZ6Wv1uQyp746xS5TJAlHC+7TvdAbFiimv7Yrjig8
            Sl94NpWKGSsOG8Ix3D+5RXs6J4uT34dgLYJ+KwieNIZMRHsri6MRZ86e7Q0ygbpd
            Is92qg3HAgMBAAECggEAHEhwh3uhWPqYtKD/3KOcRZjkK21oKwQqUaB/xNw9yHB0
            7i1jUcynBUOmxQZgKwqWHMx/Z5Z652jOAWcvPFqHrqwUavv2ZYYzRvCqkkwzhv7P
            Q21J8ERGI49zuH+bi567Bcg1popWQi3USw/pN1TWPLxT8++6zXMhHSE6RipMOwKi
            bJByGPlCcj97w/r2Mp9bNl6+40k5FpBWyVlVrWgWx6qcFh//4HD0y/qs9GhOzKCH
            Hlz+/X2C57Y5LvHq96uwZz0aZQyTdwJPberHEN3/A6UuQvbeX+qM9+X4GfNREKQD
            d6mKOThXmvykRDDlOOO+YBAURG1iwdnSwDG2SGJGrQKBgQDxzt9PKdHufiBcE2T0
            iNWBXRNXTkIXazOVLfi5841CH2Cxeh7GIsAFyext1ZkJTWS7WiWptUAYvTls+ZIV
            yz2Nq/7abHN2EdVcFayZVYkAI2uoFAS6dypKjGUqdjd4Z5+OqPh6pFsYZCbuhqqM
            S2fP5rky/lTmuyjWmHOaU8efCwKBgQDt1sB1RrOnVntLx0XXPSbeMEYPXhVZLhxz
            Jp6TE9qHld4oSkPmdTahJTpNxq+mUghirwq7yMbAW35UOKdSIScElWIvfZdtxRON
            yMtH8DSM3+PD+W3esTKuxBGCUsy9WQz5nCgJ+8+STB0T8VBFqksY2CAXAwdIqpNk
            uh2aWrCxtQKBgQCM51xu99KR48MdhFumtvma2U7E6CBRHzG+szwlt0tQBZLFuICo
            OmWkueZglkAIEKYrGjuqKakBAXquCrvaoUTsAhaO1vVtDrVCZIrgCty7TOiV15xt
            v7TQHgFxfdOTAOcULVgltxIyI2IgSi6lc+c5WZv/n/5/OP5yFgH/IuTGwwKBgQDI
            TaaEnmjLM4BfT5I4NZ44XfuDEb90/eqj6BCA2aWfrs4MrI8G1gyOJqY1vnFL56Z8
            ReMKR4trkSKRl60C9DzCFXU9fc48ek9/h238RgZb5msSL00i0aVXnnUIhuc7SHYu
            +h6WWeZ01XlfxEnQgh0A7XQJLVnDDzVXDY/E4UASSQKBgH/w375MxK7ipycRT7ma
            j242l5cGhuXTCeZZ8uwHvhjibErYXnrf/KLu4+jqlWjgR4I2Eh0GgkgVk4Zs/h2a
            BYSU18xRDLq+kxdTSH+hhzZ2fa7ZaBQi/kPNvVX3GRJJahcQj5vXdRhMt7bKaQmZ
            ZqmHvkJ7XCL/0tr9joA6KsK0
            """;

    private static final String PUBLIC_KEY = """
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4KdddOO0AJkeoCnXAHw4
            PO6cxP0balCJR0d+Y9XdR/GlWRLIHEEpbIHhettJC7SEAwn1+xq+ww8LqDrjggW9
            u3QfOcTVKVIjZWEoIe0j8NPscIWKvAqlAffGs526XIVvz90Gbi1VtTeHBY4WU47M
            rEfG4HjCjmb52WG7i6p1xJ38GuR0x6rjRkjDCGiEYz0GCRLs+azP9vClLGYqPp8R
            kILWymb3YfyN/IChzGelr9bkMqe+OsUuUyQJRwvu073QGxYopr+2K44oPEpfeDaV
            ihkrDhvCMdw/uUV7OieLk9+HYC2CfisInjSGTER7K4ujEWfOnu0NMoG6XSLPdqoN
            xwIDAQAB
            """;

    private LocalJwtKeyMaterial() {}

    static RSAPublicKey publicKey() {
        try {
            var spec = new X509EncodedKeySpec(decode(PUBLIC_KEY));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load local JWT public key", exception);
        }
    }

    static RSAPrivateKey privateKey() {
        try {
            var spec = new PKCS8EncodedKeySpec(decode(PRIVATE_KEY));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load local JWT private key", exception);
        }
    }

    private static byte[] decode(String value) {
        return Base64.getMimeDecoder().decode(value);
    }
}
