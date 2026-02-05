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
    // If omitted, server defaults to REFUND_COMPLETED.
    // 미지정 시 서버가 REFUND_COMPLETED로 처리합니다.
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
