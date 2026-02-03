package com.example.pgdemo.common.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator

@Entity
@Table(name = "headquarters")
open class Headquarters {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null

    @Column(name = "headquarters_code", nullable = false, unique = true, length = 20)
    lateinit var headquartersCode: String

    @Column(name = "name", nullable = false, length = 100)
    lateinit var name: String

    @Column(name = "business_number", nullable = false, length = 20)
    lateinit var businessNumber: String

    @Column(name = "contract_type", nullable = false, length = 20)
    lateinit var contractType: String

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "ACTIVE"

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
}
