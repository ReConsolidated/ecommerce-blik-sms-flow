package io.github.reconsolidated.smsflow.application.checkout;

import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckout;
import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckoutRepository;
import org.springframework.stereotype.Service;

@Service
public class AbandonedCheckoutService {
    private final AbandonedCheckoutRepository abandonedCheckoutRepository;

    public AbandonedCheckoutService(AbandonedCheckoutRepository abandonedCheckoutRepository) {
        this.abandonedCheckoutRepository = abandonedCheckoutRepository;
    }

    public void save(AbandonedCheckout abandonedCheckout) {
        abandonedCheckoutRepository.save(abandonedCheckout);
    }

    public AbandonedCheckout findByCartToken(String cartToken) {
        return abandonedCheckoutRepository.findById(cartToken).orElse(null);
    }

    public AbandonedCheckout findByPhoneNumberNotPaid(String phoneNumber) {
        return abandonedCheckoutRepository.findAll().stream()
                .filter(abandonedCheckout -> abandonedCheckout.getPhoneNumber().equals(phoneNumber))
                .filter(abandonedCheckout -> abandonedCheckout.getPaidForAt() == null)
                .findFirst()
                .orElse(null);
    }

    public AbandonedCheckout findByTransactionId(String transactionId) {
        return abandonedCheckoutRepository.findAll().stream()
                .filter(abandonedCheckout -> abandonedCheckout.getTransactionId().equals(transactionId))
                .findFirst()
                .orElse(null);
    }
}
