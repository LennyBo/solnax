package com.rose.solnax.process.adapters.chargepoints.tesla;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Service
@Slf4j
public class TeslaBLEAdapter {

    @Value("${tesla-ble.host}")
    private String baseUrl;
    private RestClient restClient;
    private final RetryTemplate retryTemplate;

    public TeslaBLEAdapter(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
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


    public TeslaProxyResponse chargeStart(String vin){
        return retryTemplate.execute(context ->
                restClient.post()
                        .uri("/api/1/vehicles/" + vin + "/command/charge_start")
                        .retrieve()
                        .onStatus(HttpStatusCode::is5xxServerError, (req,res) -> {
                            throw new IOException("Server error : " + res.getStatusCode());
                        })
                        .body(TeslaProxyResponse.class)
                );
    }

    public TeslaProxyResponse chargeStop(String vin){
        return retryTemplate.execute(context ->
                restClient.post()
                        .uri("/api/1/vehicles/" + vin + "/command/charge_stop")
                        .retrieve()
                        .onStatus(HttpStatusCode::is5xxServerError, (req,res) -> {
                            throw new IOException("Server error : " + res.getStatusCode());
                        })
                        .body(TeslaProxyResponse.class)
        );
    }

    public TeslaProxyResponse vehicle_data(String vin){
        return retryTemplate.execute(context ->
                restClient.get()
                        .uri("/api/1/vehicles/" + vin + "/vehicle_data")
                        .retrieve()
                        .onStatus(HttpStatusCode::is5xxServerError, (req,res) -> {
                            throw new IOException("Server error : " + res.getStatusCode());
                        })
                        .body(TeslaProxyResponse.class)
        );
    }


}
