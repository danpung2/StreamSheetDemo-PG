package com.example.pgdemo.common.domain.repository.spec

import com.example.pgdemo.common.domain.`enum`.PaymentStatus
import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.Headquarters
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.domain.Specification

object PaymentTransactionSpecifications {
    fun merchantIdEquals(merchantId: UUID?): Specification<PaymentTransaction> {
        return Specification { root, _, cb ->
            if (merchantId == null) null
            else cb.equal(root.get<Merchant>("merchant").get<UUID>("id"), merchantId)
        }
    }

    fun headquartersIdEquals(headquartersId: UUID?): Specification<PaymentTransaction> {
        return Specification { root, _, cb ->
            if (headquartersId == null) null
            else cb.equal(
                root.get<Merchant>("merchant").get<Headquarters>("headquarters").get<UUID>("id"),
                headquartersId
            )
        }
    }

    fun statusEquals(status: PaymentStatus?): Specification<PaymentTransaction> {
        return Specification { root, _, cb ->
            if (status == null) null
            else cb.equal(root.get<PaymentStatus>("status"), status)
        }
    }

    fun requestedAtGte(fromUtc: Instant?): Specification<PaymentTransaction> {
        return Specification { root, _, cb ->
            if (fromUtc == null) null
            else cb.greaterThanOrEqualTo(root.get("requestedAt"), fromUtc)
        }
    }

    fun requestedAtLte(toUtc: Instant?): Specification<PaymentTransaction> {
        return Specification { root, _, cb ->
            if (toUtc == null) null
            else cb.lessThanOrEqualTo(root.get("requestedAt"), toUtc)
        }
    }

    fun search(
        merchantId: UUID?,
        headquartersId: UUID?,
        status: PaymentStatus?,
        fromUtc: Instant?,
        toUtc: Instant?
    ): Specification<PaymentTransaction> {
        return Specification
            .where(merchantIdEquals(merchantId))
            .and(headquartersIdEquals(headquartersId))
            .and(statusEquals(status))
            .and(requestedAtGte(fromUtc))
            .and(requestedAtLte(toUtc))
    }
}
