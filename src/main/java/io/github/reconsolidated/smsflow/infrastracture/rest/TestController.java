package io.github.reconsolidated.smsflow.infrastracture.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.reconsolidated.smsflow.application.checkout.CheckoutsService;
import io.github.reconsolidated.smsflow.application.payment.BlikConnectorService;
import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RequiredArgsConstructor
@Controller
public class TestController {
    private final AbandonedCheckoutRepository abandonedCheckoutRepository;
    private final CheckoutsService checkoutsService;
    private final BlikConnectorService blikConnectorService;
    @GetMapping("/test")
    public ResponseEntity<?> test() {
        blikConnectorService.payForOrder("605350390", "123456");
        return ResponseEntity.ok(abandonedCheckoutRepository.findAll());
    }

    @PostMapping(value="/tpay-webhook", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> tpayWebhook(@RequestParam Map<String, String> formData) {
        System.out.println("Tpay webhook received: " + formData);
        Boolean status = Boolean.parseBoolean(formData.get("tr_status"));
        String transactionTitle = formData.get("tr_id");
        checkoutsService.processTransactionWebhook(transactionTitle, status);
        return ResponseEntity.ok("TRUE");
    }

    @PostMapping(value = "/webhook", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> webhook(@RequestParam Map<String, String> formData) {
        // oczekiwany format: "TAK 123456"
        System.out.println("Webhook received: " + formData);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map<String, Object> map = objectMapper.readValue(formData.get("message"), Map.class);
            blikConnectorService.payForOrder(
                    (String) map.get("msisdn"),
                    extractBlikToken((String) map.get("msg"))
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok("Webhook received");
    }

    private String extractBlikToken(String message) {
        String token = message.split(" ")[1];
        if (token.length() != 6) {
            throw new IllegalArgumentException("Invalid BLIK token length");
        }
        if (!token.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid BLIK token format");
        }
        return token;
    }
}
