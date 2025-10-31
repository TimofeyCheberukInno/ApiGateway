package com.inno.impl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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


}
