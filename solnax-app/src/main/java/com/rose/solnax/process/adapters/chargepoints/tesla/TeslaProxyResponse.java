package com.rose.solnax.process.adapters.chargepoints.tesla;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class TeslaProxyResponse {

    Response response;

    @Getter
    @AllArgsConstructor
    public class Response {
        Boolean result;
        String reason;
        String vin;
        String command;
    }

}
