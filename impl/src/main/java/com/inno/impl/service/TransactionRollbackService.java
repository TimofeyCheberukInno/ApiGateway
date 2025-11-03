package com.inno.impl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class TransactionRollbackService {
    private final WebClient userServiceClient;
    private final WebClient authServiceClient;

    @Autowired
    public TransactionRollbackService(
            WebClient userServiceWebClient,
            WebClient authServiceWebClient)
    {
        this.userServiceClient = userServiceWebClient;
        this.authServiceClient = authServiceWebClient;
    }

    public Mono<Void> rollbackUserRegistration(String email, Long userId) {
        log.atInfo()
                .addArgument(email)
                .addArgument(userId)
                .log("Starting rollback for user registration: email={}, userId={}");

        return Mono.when(
                rollbackAuthCredentials(email),
                rollbackUserInfo(userId)
        ).doOnSuccess(_ -> {
            log.atInfo()
                    .addArgument(email)
                    .log("Successfully rolled back user registration for email: {}");
        }).doOnError(error -> {
            log.atError()
                    .setCause(error.getCause())
                    .addArgument(email)
                    .log("Failed to rollback user registration for email: {}");
        });
    }

    public Mono<Void> rollbackAuthCredentials(String login) {
        log.atInfo()
                .addArgument(login)
                .log("Rolling back auth credentials for login: {}");

        return authServiceClient.put()
                .uri("/api/auth/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("login", login))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(_ -> {
                    log.atInfo()
                            .addArgument(login)
                            .log("Successfully deleted auth credentials: {}");
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().value() == 404) {
                        log.atInfo()
                                .addArgument(login)
                                .log("Auth credentials not found for deactivation: {}");
                        return Mono.empty();
                    }
                    return Mono.error(ex);
                })
                .onErrorResume(_ -> {
                    log.atWarn()
                            .addArgument(login)
                            .log("All rollback attempts failed for auth credentials: {}");
                    return logFailedTransaction("AUTH_ROLLBACK",
                            String.format("Failed to rollback auth credentials for login: %s", login));
                });
    }

    private Mono<Void> rollbackUserInfo(Long userId) {
        if(userId == null){
            log.atError().log("No user ID provided, skipping user profile rollback");
            return Mono.empty();
        }

        log.atInfo()
                .addArgument(userId)
                .log("Rolling back user info for userId: {}");

        return userServiceClient.delete()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(_ -> log.atInfo().log("Successfully updated user profile"))
                .onErrorResume(ex -> {
                    if (ex instanceof WebClientResponseException webEx) {
                        if (webEx.getStatusCode().value() == 404) {
                            log.atInfo()
                                    .addArgument(userId)
                                    .log("User profile not found for rollback: {}");
                            return Mono.empty();
                        }
                    }
                    log.atError()
                            .setCause(ex.getCause())
                            .addArgument(userId)
                            .log("Failed to delete user profile: {}");
                    return logFailedTransaction("USER_ROLLBACK",
                            String.format("Failed to delete user profile with ID: %d", userId));

                });
    }

    public Mono<Void> logFailedTransaction(String operation, String details) {
        return Mono.fromRunnable(() -> {
            String logMessage = String.format(
                    "FAILED_TRANSACTION [%s] at %s: %s",
                    operation,
                    LocalDateTime.now(),
                    details
            );

            log.atError().log(logMessage);
        });
    }
}
