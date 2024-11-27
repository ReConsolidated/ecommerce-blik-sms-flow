package io.github.reconsolidated.smsflow.domain.checkout;

import io.github.reconsolidated.smsflow.domain.checkout.AbandonedCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AbandonedCheckoutRepository extends JpaRepository<AbandonedCheckout, String> {
}
