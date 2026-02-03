package com.example.pgdemo.common.domain.entity

import com.example.pgdemo.common.domain.`enum`.BusinessType
import com.example.pgdemo.common.domain.`enum`.StoreType
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
import java.time.LocalDate
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator

@Entity
@Table(name = "merchant")
open class Merchant {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null

    @Column(name = "merchant_code", nullable = false, unique = true, length = 30)
    lateinit var merchantCode: String

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "headquarters_id")
    var headquarters: Headquarters? = null

    @Column(name = "name", nullable = false, length = 100)
    lateinit var name: String

    @Column(name = "store_number")
    var storeNumber: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "store_type", nullable = false, length = 20)
    lateinit var storeType: StoreType

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 30)
    lateinit var businessType: BusinessType

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "ACTIVE"

    @Column(name = "contract_start_date", nullable = false)
    lateinit var contractStartDate: LocalDate

    @Column(name = "contract_end_date")
    var contractEndDate: LocalDate? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
}
