package com.inno.impl.security;

import com.inno.impl.exception.AuthenticationException;
import com.inno.impl.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.function.Function;

@Slf4j
@Component
public class JwtTokenValidator {
    @Value("${jwt.secret.key}")
    private String secret;

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts
                    .parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.atWarn()
                    .addArgument(e.getMessage())
                    .log("JWT token expired: {}");
            throw new TokenExpiredException("Token is expired: " + e.getMessage());
        } catch (SignatureException e) {
            log.atError()
                    .addArgument(e.getMessage())
                    .log("Invalid JWT signature: {}");
            throw new AuthenticationException("Invalid token signature: " + e.getMessage());
        } catch (MalformedJwtException e) {
            log.atError()
                    .addArgument(e.getMessage())
                    .log("Malformed JWT token: {}");
            throw new AuthenticationException("Invalid token format: " + e.getMessage());
        }
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
