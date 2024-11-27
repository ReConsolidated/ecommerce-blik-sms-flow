package io.github.reconsolidated.smsflow.application.checkout;

import io.github.reconsolidated.smsflow.application.payment.BlikConnectorService;
import io.github.reconsolidated.smsflow.application.notification.EmailService;
import io.github.reconsolidated.smsflow.application.sms.SmsService;
import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckout;
import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckoutRepository;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class CheckoutsService {
    private final EmailService emailService;
    private final AbandonedCheckoutRepository abandonedCheckoutRepository;
    private final BlikConnectorService blikConnectorService;
    private final SmsService smsService;
    @Value("${apiKeys.shopify.access}")
    private String shopifyAccessToken;

    private final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .responseTimeout(Duration.ofMillis(15000))
            .doOnConnected(conn ->
            conn.addHandlerLast(new ReadTimeoutHandler(15000, TimeUnit.MILLISECONDS))
            .addHandlerLast(new WriteTimeoutHandler(15000, TimeUnit.MILLISECONDS)));

    @Scheduled(fixedRate = 10000)
    public void fetchCheckouts() {
        List<AbandonedCheckout> checkouts = fetchRecentCheckouts();
        for (AbandonedCheckout checkout : checkouts) {
            Optional<AbandonedCheckout> inRepo = abandonedCheckoutRepository.findById(checkout.getCartToken());
            if (inRepo.isEmpty()) {
                processAbandonedCheckout(abandonedCheckoutRepository.save(checkout));
            } else if (inRepo.get().getPhoneNumber() == null && checkout.getPhoneNumber() != null) {
                inRepo.get().setPhoneNumber(checkout.getPhoneNumber());
                processAbandonedCheckout(abandonedCheckoutRepository.save(checkout));
            }
            // handle case when something went wrong with creating blik transaction - basically a retry mechanism
            else if (checkout.getBlikCodeReceivedAt() != null
                    && checkout.getBlikCodeReceivedAt().isAfter(LocalDateTime.now().minusSeconds(10))
                    && checkout.getBlikCodeReceivedAt().isBefore(LocalDateTime.now().plusMinutes(1))
                    && checkout.getTransactionId() == null) {
                blikConnectorService.processBlikCode(checkout.getPhoneNumber(), checkout.getLastBlikCode(), checkout);
            }
        }
    }

    public void processAbandonedCheckout(AbandonedCheckout checkout) {
        System.out.println("Processing checkout: " + checkout);
        smsService.sendSms(checkout.getPhoneNumber(), "Czesc, tu Marcin z Pluszusiowo.pl. Widzimy, ze nie zaplaciles za pluszaka. " +
                "Zeby zaplacic, odpisz 'TAK KOD_BLIK' (np. 'TAK 123456')");
    }

    public AbandonedCheckout fulfillCheckout(AbandonedCheckout checkout) {
        WebClient client = WebClient.builder()
                .baseUrl("https://quickstart-d96ed6ad.myshopify.com/admin/api/2024-04/orders.json")
                .defaultHeader("X-Shopify-Access-Token", shopifyAccessToken)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        Map response = client.post()
                .exchangeToMono(clientResponse -> {
            return clientResponse.bodyToMono(Map.class);
        }).block();
        System.out.println(response);
        checkout.setSubmittedToShopifyAt(LocalDateTime.now());
        abandonedCheckoutRepository.save(checkout);
        return checkout;
    }

    private List<AbandonedCheckout> fetchRecentCheckouts() {
        WebClient client = WebClient.builder()
                .baseUrl("https://quickstart-d96ed6ad.myshopify.com/admin/api/2024-04/checkouts.json?created_at_min="
                        + LocalDateTime.now().minusDays(3))
                .defaultHeader("X-Shopify-Access-Token", shopifyAccessToken)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        Map response = client.get().exchangeToMono(clientResponse -> {
            return clientResponse.bodyToMono(Map.class);
        }).block();

        List<Map<String, Object>> checkouts = (List<Map<String, Object>>) response.get("checkouts");

        List<AbandonedCheckout> results = new ArrayList<>();
        for (Map<String, Object> entry : checkouts) {
            Map<String, Object> customer = (Map<String, Object>) entry.get("customer");
            Map<String, Object> smsConsent = (Map<String, Object>) customer.get("sms_marketing_consent");
            LocalDateTime completedAt = null;
            if (entry.get("completed_at") != null) {
                completedAt = LocalDateTime.parse(Objects.toString(entry.get("completed_at")));
            }
            AbandonedCheckout abandonedCheckout = AbandonedCheckout.builder()
                    .cartToken(Objects.toString(entry.get("cart_token")))
                    .token(Objects.toString(entry.get("token")))
                    .phoneNumber(Objects.toString(entry.get("phone")))
                    .email(Objects.toString(entry.get("email")))
                    .firstName(Objects.toString(customer.get("first_name")))
                    .lastName(Objects.toString(customer.get("last_name")))
                    .acceptsMarketing((Boolean) entry.get("buyer_accepts_marketing"))
                    .totalPrice(Double.parseDouble(entry.get("total_price").toString()))
                    .completedAt(completedAt)
                    .importedAt(LocalDateTime.now())
                    .build();
            if (smsConsent != null) {
                abandonedCheckout.setSmsMarketingConsentState(Objects.toString(smsConsent.get("state")));
                abandonedCheckout.setSmsMarketingConsentStateUpdatedAt(Objects.toString(smsConsent.get("consent_updated_at")));
            }

            results.add(abandonedCheckout);
        }
        return results;
    }

    public void processTransactionWebhook(String transactionTitle, Boolean status) {
        AbandonedCheckout checkout = abandonedCheckoutRepository.findAll().stream()
                .filter(abandonedCheckout -> Objects.equals(abandonedCheckout.getTransactionTitle(), transactionTitle))
                .findFirst()
                .orElse(null);
        if (checkout == null) {
            System.out.println("No checkout found for transaction title: " + transactionTitle);
            return;
        }
        if (status && checkout.getPaidForAt() == null) {
            checkout.setPaidForAt(LocalDateTime.now());
            abandonedCheckoutRepository.save(checkout);
            // fulfillCheckout(checkout); - implement functionality to automatically fulfill in the future
            System.out.println("[SMS] Płatność zakończona sukcesem.");
            try{
                smsService.sendSms(checkout.getPhoneNumber(), "Dziękujemy za zakup. Zamówienie zostanie wkrótce wysłane.");
            } catch (Exception ignored) {
                System.out.println("Nie udało się wysłać SMSa z potwierdzeniem.");
            }
            try {
                emailService.getRecipients().forEach(recipient -> {
                    emailService.sendSimpleMessage(recipient, "Zapłacono za zamówienie",
                            "Zapłacono za zamówienie o numerze: " + checkout.getCartToken());
                });
            } catch (Exception ignored) {
                System.out.println("Nie udało się wysłać emaila z potwierdzeniem.");
            }

        } else if (!status) {
            smsService.sendSms(checkout.getPhoneNumber(), "Niestety, płatność nie powiodła się. Spróbuj ponownie.");
            System.out.println("[SMS] Płatność zakończona niepowodzeniem.");
        } else {
            System.out.println("Status pozytywny, ale paidForAt już uzupełnione. Ignoruję.");
        }
    }

}
