package io.github.reconsolidated.smsflow.domain.checkout;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AbandonedCheckout {
    @Id
    private String cartToken;
    private String token;
    private String phoneNumber;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean acceptsMarketing;
    private String smsMarketingConsentState;
    private String smsMarketingConsentStateUpdatedAt;
    private Double totalPrice;
    @Setter
    private LocalDateTime importedAt;
    @Setter
    private LocalDateTime firstSmsSentAt;
    @Setter
    private LocalDateTime paidForAt;
    @Setter
    private LocalDateTime submittedToShopifyAt;
    @Setter
    private LocalDateTime completedAt;
    private LocalDateTime blikCodeReceivedAt;
    @Setter
    private String transactionId;
    @Setter
    private String transactionTitle;
    private String lastBlikCode;
}
