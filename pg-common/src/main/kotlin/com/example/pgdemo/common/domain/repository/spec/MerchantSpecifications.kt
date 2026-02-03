package com.example.pgdemo.common.domain.repository.spec

import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.Headquarters
import java.util.UUID
import org.springframework.data.jpa.domain.Specification

object MerchantSpecifications {
    fun headquartersIdEquals(headquartersId: UUID?): Specification<Merchant> {
        return Specification { root, _, cb ->
            if (headquartersId == null) null
            else cb.equal(root.get<Headquarters>("headquarters").get<UUID>("id"), headquartersId)
        }
    }

    fun statusEquals(status: String?): Specification<Merchant> {
        return Specification { root, _, cb ->
            if (status.isNullOrBlank()) null
            else cb.equal(root.get<String>("status"), status)
        }
    }

    fun merchantQueryContainsIgnoreCase(merchantQuery: String?): Specification<Merchant> {
        return Specification { root, _, cb ->
            val q = merchantQuery?.trim()?.takeIf { it.isNotBlank() } ?: return@Specification null
            val like = "%" + q.lowercase() + "%"
            cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("merchantCode")), like)
            )
        }
    }

    fun search(headquartersId: UUID?, status: String?, merchantQuery: String?): Specification<Merchant> {
        return Specification
            .where(headquartersIdEquals(headquartersId))
            .and(statusEquals(status))
            .and(merchantQueryContainsIgnoreCase(merchantQuery))
    }
}
