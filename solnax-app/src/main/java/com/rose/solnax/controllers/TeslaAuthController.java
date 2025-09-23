package com.rose.solnax.controllers;

import com.rose.solnax.model.entity.TeslaAuth;
import com.rose.solnax.model.repository.TeslaAuthRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@Slf4j
@RequiredArgsConstructor
public class TeslaAuthController {

    private final TeslaAuthRepository teslaAuthRepository;

    @GetMapping("/callback")
    @Transactional
    public ResponseEntity<String> callback(
            @RequestParam("code") Optional<String> code,
            @RequestParam("state") Optional<String> state) {

        // At this point you have the authorization code from Tesla.
        TeslaAuth build = TeslaAuth.builder().token(code.orElse("")).build();
        teslaAuthRepository.save(build);
        log.info("code:{}, state:{}", code, state);
        return ResponseEntity.ok("Received code: " + code);
    }
}