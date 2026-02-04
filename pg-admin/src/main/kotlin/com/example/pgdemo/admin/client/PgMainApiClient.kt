package com.example.pgdemo.admin.client

import java.util.UUID
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class PgMainApiClient(
    private val restClient: RestClient
) {
    fun listPayments(
        page: Int,
        size: Int,
        fromUtc: java.time.Instant?,
        toUtc: java.time.Instant?,
        headquartersId: UUID?,
        merchantId: UUID?,
        status: com.example.pgdemo.common.domain.enum.PaymentStatus?
    ): PageResponse<PaymentResponse>? {
        val responseType = object : ParameterizedTypeReference<PageResponse<PaymentResponse>>() {}
        return restClient.get()
            .uri { builder ->
                builder.path("/api/v1/payments")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .queryParam("sort", "requestedAt,desc")
                    .queryParam("sort", "id,desc")
                    .apply {
                        fromUtc?.let { queryParam("from", it) }
                        toUtc?.let { queryParam("to", it) }
                        headquartersId?.let { queryParam("headquartersId", it) }
                        merchantId?.let { queryParam("merchantId", it) }
                        status?.let { queryParam("status", it) }
                    }
                    .build()
            }
            .retrieve()
            .body(responseType)
    }

    fun getPayment(id: UUID): PaymentResponse? {
        return restClient.get()
            .uri("/api/v1/payments/{id}", id)
            .retrieve()
            .body(PaymentResponse::class.java)
    }

    fun listMerchants(page: Int, size: Int): PageResponse<MerchantResponse>? {
        val responseType = object : ParameterizedTypeReference<PageResponse<MerchantResponse>>() {}
        return restClient.get()
            .uri { builder ->
                builder.path("/api/v1/merchants")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .queryParam("sort", "createdAt,desc")
                    .build()
            }
            .retrieve()
            .body(responseType)
    }

    fun getMerchant(id: UUID): MerchantResponse? {
        return restClient.get()
            .uri("/api/v1/merchants/{id}", id)
            .retrieve()
            .body(MerchantResponse::class.java)
    }
}
