package com.example.pgdemo.admin.client

import com.example.pgdemo.common.domain.enum.BusinessType
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.StoreType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val number: Int = 0,
    val size: Int = 0
)

data class PaymentResponse(
    val id: UUID,
    val merchantId: UUID,
    val orderId: String,
    val amount: Long,
    val paymentMethod: String,
    val status: PaymentStatus,
    val requestedAt: Instant,
    val processedAt: Instant?,
    val completedAt: Instant?,
    val failureReason: String?
)

data class MerchantResponse(
    val id: UUID,
    val merchantCode: String,
    val name: String,
    val storeType: StoreType,
    val businessType: BusinessType,
    val status: String,
    val contractStartDate: LocalDate,
    val contractEndDate: LocalDate?,
    val storeNumber: Int?,
    val headquartersId: UUID?
)
