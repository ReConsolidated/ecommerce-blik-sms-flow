package io.github.reconsolidated.smsflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AbandonedCheckoutRepository extends JpaRepository<AbandonedCheckout, String> {
}
