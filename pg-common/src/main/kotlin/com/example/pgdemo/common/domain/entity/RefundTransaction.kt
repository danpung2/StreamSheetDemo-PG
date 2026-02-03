package com.example.pgdemo.common.domain.entity

import com.example.pgdemo.common.domain.`enum`.RefundStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator

@Entity
@Table(name = "refund_transaction")
open class RefundTransaction {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    lateinit var payment: PaymentTransaction

    @Column(name = "refund_amount", nullable = false)
    var refundAmount: Long = 0L

    @Column(name = "refund_reason", length = 255)
    var refundReason: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    lateinit var status: RefundStatus

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    lateinit var requestedAt: Instant

    @Column(name = "processed_at")
    var processedAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
}
