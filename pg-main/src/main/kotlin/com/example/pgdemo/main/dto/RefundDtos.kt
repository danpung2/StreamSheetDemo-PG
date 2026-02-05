package com.example.pgdemo.main.dto

import com.example.pgdemo.common.domain.enum.RefundStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID

data class RefundRequest(
    @field:NotNull(message = "refundAmount is required")
    @field:Positive(message = "refundAmount must be positive")
    val refundAmount: Long?,
    val refundReason: String?,
    // /api/v1/payments/{id}/refund는 '환불 요청 생성'만 처리하므로 status/failureReason은 서버에서 무시됩니다.
    val status: RefundStatus? = null,
    val failureReason: String? = null
)

data class RefundResponse(
    val id: UUID,
    val paymentId: UUID,
    val refundAmount: Long,
    val refundReason: String?,
    val status: RefundStatus,
    val requestedAt: Instant,
    val processedAt: Instant?,
    val completedAt: Instant?,
    val failureReason: String?
)
