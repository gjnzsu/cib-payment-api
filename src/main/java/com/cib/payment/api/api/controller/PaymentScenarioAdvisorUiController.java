package com.cib.payment.api.api.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentScenarioAdvisorUiController {
    @GetMapping({"/payment-scenario-advisor", "/payment-scenario-advisor/"})
    ResponseEntity<ClassPathResource> advisorUi() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/payment-scenario-advisor/index.html"));
    }
}
