package io.github.reconsolidated.smsflow.application.payment;

import io.github.reconsolidated.smsflow.application.sms.SmsService;
import io.github.reconsolidated.smsflow.application.checkout.AbandonedCheckoutService;
import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckout;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class BlikConnectorService {
    private final AbandonedCheckoutService abandonedCheckoutService;
    private final SmsService smsService;
    @Value("${apiKeys.tpay-prod.clientId}")
    private String tpayClientId;
    @Value("${apiKeys.tpay-prod.secret}")
    private String tpayClientSecret;

    private final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
            .responseTimeout(Duration.ofMillis(15000))
            .doOnConnected(conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(15000, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(15000, TimeUnit.MILLISECONDS)));


    public void payForOrder(String phoneNumber, String blikToken) {
        System.out.println("Paying for order with token: " + blikToken + " for phone number: " + phoneNumber);
        AbandonedCheckout abandonedCheckout = abandonedCheckoutService.findByPhoneNumberNotPaid(phoneNumber);
        if (abandonedCheckout == null) {
            abandonedCheckout = abandonedCheckoutService.findByPhoneNumberNotPaid("+48" + phoneNumber);
        }
        if (abandonedCheckout == null) {
            System.out.println("[SMS] No abandoned checkout found for phone number: " + phoneNumber);
            smsService.sendSms(phoneNumber, "Nie znaleziono zamówienia dla tego numeru telefonu");
            return;
        }
        abandonedCheckout.setBlikCodeReceivedAt(LocalDateTime.now());
        abandonedCheckout.setLastBlikCode(blikToken);
        abandonedCheckoutService.save(abandonedCheckout);
        processBlikCode(phoneNumber, blikToken, abandonedCheckout);
    }

    public void processBlikCode(String phoneNumber, String blikToken, AbandonedCheckout abandonedCheckout) {
        Map result = createTransaction(abandonedCheckout.getTotalPrice());
        String transactionId = (String) result.get("transactionId");
        String transactionTitle = (String) result.get("title");
        System.out.println("Transaction created: " + transactionId + ", " + transactionTitle);
        abandonedCheckout.setTransactionId(transactionId);
        abandonedCheckout.setTransactionTitle(transactionTitle);
        abandonedCheckoutService.save(abandonedCheckout);
        payForTransaction(phoneNumber, transactionId, blikToken);

    }

    public Map createTransaction(Double amount) {
        WebClient client = createWebClient();
        Map response = client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of(
                        "amount", amount,
                        "description", "Platnosc SMS BLIK",
                        "payer", Map.of(
                                "phone", "605350390",
                                "email", "gracjanpasik@gmail.com",
                                "name", "Gracjan Pasik"
                            ),
                        "pay", Map.of(
                                "groupId", "150"
                            ),
                        "callbacks", Map.of(
                                "notification", Map.of(
                                        "url", "https://sms-flow.fly.dev/tpay-webhook"
                                )
                            )
                        )
                ))
                .exchangeToMono(clientResponse -> {
                    System.out.println(clientResponse.statusCode());
                    return clientResponse.bodyToMono(Map.class);
                }).block();
        System.out.println(response);
        return response;
    }

    public String payForTransaction(String phoneNumber, String transactionId, String blikToken) {
        WebClient client = createWebClient();
        Map response = client.post()
                .uri("/transactions/" + transactionId + "/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of(
                        "groupId", "150",
                        "blikPaymentData", Map.of(
                                "blikToken", blikToken
                        )
                )))
                .exchangeToMono(clientResponse -> {
                    System.out.println(clientResponse.statusCode());
                    return clientResponse.bodyToMono(Map.class);
                }).block();
        System.out.println(response);
        Map<String, Object> payments = (Map<String, Object>) response.get("payments");
        List<Map<String, Object>> errors = (List<Map<String, Object>>) payments.get("errors");
        if (errors != null && errors.size() > 0) {
            System.out.println("Error: " + errors);
            System.out.println("[SMS] Kod jest nieprawidłowy, bądź utracił ważność.");
            smsService.sendSms(phoneNumber, "Kod jest nieprawidłowy, bądź utracił ważność.");
            return null;
        }
        return response.get("transactionId").toString();
    }

    private WebClient createWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.tpay.com")
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setBearerAuth(authorizeForAccessToken());
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private String authorizeForAccessToken() {
        WebClient client = WebClient.builder()
                .baseUrl("https://api.tpay.com/oauth/auth")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        Map response = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of(
                        "client_id", tpayClientId,
                        "client_secret", tpayClientSecret,
                        "scope", "read write"))
                )
                .exchangeToMono(clientResponse -> {
                    System.out.println(clientResponse.statusCode());
                    return clientResponse.bodyToMono(Map.class);
                }).block();
        System.out.println(response);
        return (String) response.get("access_token");
    }
}
