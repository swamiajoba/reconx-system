package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Trade;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Static factories that return Specification<Trade> instances.
 * Compose with .and()/.or() in the service layer.
 */
public final class TradeSpecifications {

    private TradeSpecifications() {}

    public static Specification<Trade> hasStatus(String status) {
        return (root, q, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Trade> tradeDateBetween(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null) return cb.lessThanOrEqualTo(root.get("tradeDate"), to);
            if (to == null)   return cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
            return cb.between(root.get("tradeDate"), from, to);
        };
    }

    public static Specification<Trade> hasCounterparty(Long counterpartyId) {
        return (root, q, cb) -> counterpartyId == null
                ? cb.conjunction()
                : cb.equal(root.get("counterparty").get("id"), counterpartyId);
    }
}
