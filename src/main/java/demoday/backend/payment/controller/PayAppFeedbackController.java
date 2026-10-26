package demoday.backend.payment.controller;

import demoday.backend.payment.dto.PayAppFeedbackRequest;
import demoday.backend.payment.service.PayAppFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PayAppFeedbackController {

    private static final String SUCCESS = "SUCCESS";

    private final PayAppFeedbackService feedbackService;

    @PostMapping(
            value = "/feedback",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> feedback(
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        feedbackService.process(
                PayAppFeedbackRequest.from(
                        parameters.toSingleValueMap()
                )
        );

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(SUCCESS);
    }
}
