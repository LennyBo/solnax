package com.rose.solnax.process.adapters.chargepoints.tesla;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rose.solnax.process.adapters.chargepoints.tesla.model.TeslaWallConnectorStatus;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Service
@Slf4j
public class TWCManagerAdapter {

    @Value("${TWCManager.host}")
    private String baseUrl;
    private RestClient restClient;
    private final RetryTemplate retryTemplate;

    private final ObjectMapper objectMapper;

    public TWCManagerAdapter(RetryTemplate retryTemplate, ObjectMapper objectMapper) {
        this.retryTemplate = retryTemplate;
        this.objectMapper = objectMapper;
    }


    @PostConstruct
    public void init(){
        buildRestClient();
    }

    private void buildRestClient() {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @SneakyThrows
    public TeslaWallConnectorStatus getTWCStatus() {
        return retryTemplate.execute(context ->
                {
                    String response = restClient.get()
                            .uri("/TWCapi.php?GetStatus=1")
                            .retrieve()
                            .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                                throw new IOException("Server error : " + res.getStatusCode());
                            })
                            .body(String.class);
                    return objectMapper.readValue(response,TeslaWallConnectorStatus.class);
                }
        );
    }
}
