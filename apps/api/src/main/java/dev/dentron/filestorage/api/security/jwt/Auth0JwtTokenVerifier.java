package dev.dentron.filestorage.api.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import dev.dentron.filestorage.api.security.ServiceDetails;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
public class Auth0JwtTokenVerifier implements JwtTokenVerifier {
    public final Algorithm algorithm;
    public final JWTVerifier verifier;

    public Auth0JwtTokenVerifier(String publicKey) {
        this.algorithm = Algorithm.RSA256(retrieveRsaPublicKey(publicKey));
        this.verifier = JWT.require(algorithm).build();
    }

    private static RSAPublicKey retrieveRsaPublicKey(String publicKey) {
        String safe = publicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        try {
            byte[] der = Base64.getDecoder().decode(safe);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var key = factory.generatePublic(new X509EncodedKeySpec(der));

            if (!(key instanceof RSAPublicKey rsaKey)) {
                log.error("RSA Public Key is not a RSAPublicKey");
                return null;
            }

            return rsaKey;

        } catch (NoSuchAlgorithmException e) {
            log.error("Unresolved algorithm: {}", e.getMessage());
            return null;
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JwtToken validate(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return new JwtToken(
                    jwt.getSubject(),
                    jwt.getExpiresAtAsInstant(),
                    jwt.getAudience()
            );
        } catch (JWTVerificationException e) {
            return null;
        }
    }
}
