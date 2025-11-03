package com.inno.impl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient userServiceWebClient(@Value("${USER_SERVICE_URI}") String userServiceUri) {
        return WebClient.builder()
                .baseUrl(userServiceUri)
                .build();
    }

    @Bean
    public WebClient authServiceWebClient(@Value("${AUTH_SERVICE_URI}") String authServiceUri) {
        return WebClient.builder()
                .baseUrl(authServiceUri)
                .build();
    }
}
