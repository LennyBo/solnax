package com.rose.solnax.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainController {

    @GetMapping("/hello")
    public String hello(){
        return "V0.4";
    }

    @GetMapping("/where")
    public String where() {
        return TrainController.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString();
    }

}
