package com.inno.impl.security.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inno.impl.exception.AuthenticationException;
import com.inno.impl.exception.TokenExpiredException;
import com.inno.impl.security.jwt.JwtTokenValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class AuthCheckFilter extends AbstractGatewayFilterFactory<AuthCheckFilter.Config> {
    private final JwtTokenValidator jwtTokenValidator;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuthCheckFilter(
            JwtTokenValidator jwtTokenValidator,
            ObjectMapper objectMapper
    )
    {
        super(AuthCheckFilter.Config.class);
        this.jwtTokenValidator = jwtTokenValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            log.atInfo()
                    .addArgument(exchange.getRequest().getURI().getPath())
                    .log("Processing request URI : {}");

            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            if(authHeader == null || !authHeader.startsWith("Bearer ")){
                log.atError()
                        .log("Token is absent or invalid format");

                return sendOnError(exchange, "Token is absent or invalid", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            return Mono.fromCallable(() -> jwtTokenValidator.extractEmail(token))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(email -> {
                        if (email == null || email.isBlank()) {
                            return sendOnError(exchange, "Email is absent or invalid", HttpStatus.UNAUTHORIZED);
                        }

                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-User-Email", email)
                                .build();
                        log.atInfo()
                                .addArgument(email)
                                .log("Request authorized for user: {}");

                        return chain.filter(exchange.mutate().request(mutated).build());
                    })
                    .onErrorResume(ex -> {
                        if (ex instanceof TokenExpiredException) {
                            return sendOnError(exchange, "Token expired", HttpStatus.UNAUTHORIZED);
                        }
                        if (ex instanceof AuthenticationException) {
                            return sendOnError(exchange, "Authentication failed", HttpStatus.UNAUTHORIZED);
                        }
                        log.atError()
                                .setCause(ex)
                                .log("Auth check failed");
                        return sendOnError(exchange, "Internal authentication error", HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        });
    }

    private Mono<Void> sendOnError(
            ServerWebExchange exchange,
            String msg,
            HttpStatus httpStatus
    )
    {
        log.atError()
                .addArgument(msg)
                .log("Sending error response to request : {}");

        try {
            ServerHttpResponse serverHttpResponse = exchange.getResponse();
            serverHttpResponse.setStatusCode(httpStatus);
            serverHttpResponse.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            String json = objectMapper.writeValueAsString(Map.of("message", msg));

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataBuffer dataBuffer = serverHttpResponse.bufferFactory().wrap(bytes);
            Mono<DataBuffer> dataBufferMono = Mono.just(dataBuffer);
            return serverHttpResponse.writeWith(dataBufferMono);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Config {}
}
