package com.inno.impl.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inno.impl.dto.RegisterRequest;
import com.inno.impl.dto.RegisterResponse;
import com.inno.impl.service.TransactionRollbackService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class RegistrationFilter extends AbstractGatewayFilterFactory<RegistrationFilter.Config> {
    private static final String REGISTER_PATH = "api/auth/register";
    private final WebClient userServiceClient;
    private final WebClient authServiceClient;
    private final TransactionRollbackService transactionRollbackService;
    private final ObjectMapper objectMapper;

    public RegistrationFilter(
            WebClient userServiceWebClient,
            WebClient authServiceWebClient,
            TransactionRollbackService transactionRollbackService,
            ObjectMapper objectMapper)
    {
        super(Config.class);
        this.userServiceClient = userServiceWebClient;
        this.authServiceClient = authServiceWebClient;
        this.transactionRollbackService = transactionRollbackService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if(REGISTER_PATH.equals(exchange.getRequest().getURI().getPath())) {
                return handleRegistration(exchange);
            }
            return chain.filter(exchange);
        };
    }

    private Mono<Void> handleRegistration(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    try{
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        RegisterRequest request = objectMapper.readValue(bytes, RegisterRequest.class);
                        return registerUser(exchange, request);
                    } catch (Exception e){
                        return sendError(exchange, "Invalid request format", HttpStatus.BAD_REQUEST);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    private Mono<Void> registerUser(ServerWebExchange exchange, RegisterRequest request) {
        return authServiceClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "login", request.email(),
                        "password", request.password()
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(authResponse -> userServiceClient.post()
                        .uri("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "name", request.name(),
                                "surname", request.surname(),
                                "birthDate", request.birthDate(),
                                "email", request.email()
                        ))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .flatMap(userResponse -> sendSuccess(
                                exchange,
                                new RegisterResponse(
                                        Long.valueOf(userResponse.get("id").toString()),
                                        authResponse.get("login").toString(),
                                        "User has been registered successfully!"
                                )
                        ))
                        .onErrorResume(ex -> transactionRollbackService.rollbackAuthCredentials(authResponse.get("login").toString())
                                .then(sendError(exchange, "Failed to create user profile", HttpStatus.INTERNAL_SERVER_ERROR))
                        )
                        .onErrorResume(e -> sendError(exchange, "Registration failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR))
                );
    }

    private Mono<Void> sendSuccess(ServerWebExchange exchange, RegisterResponse response) {
        try{
            String json = objectMapper.writeValueAsString(response);
            ServerHttpResponse serverHttpResponse = exchange.getResponse();
            serverHttpResponse.setStatusCode(HttpStatus.CREATED);
            serverHttpResponse.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataBuffer dataBuffer = serverHttpResponse.bufferFactory().wrap(bytes);
            Mono<DataBuffer> dataBufferMono = Mono.just(dataBuffer);
            return serverHttpResponse.writeWith(dataBufferMono);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<Void> sendError(
            ServerWebExchange exchange,
            String msg,
            HttpStatus httpStatus
    )
    {
        try{
            String json = objectMapper.writeValueAsString(new RegisterResponse(null, null, msg));
            ServerHttpResponse serverHttpResponse = exchange.getResponse();
            serverHttpResponse.setStatusCode(httpStatus);
            serverHttpResponse.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataBuffer dataBuffer = serverHttpResponse.bufferFactory().wrap(bytes);
            Mono<DataBuffer> dataBufferMono = Mono.just(dataBuffer);
            return serverHttpResponse.writeWith(dataBufferMono);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Config { }
}
