package com.example.pgdemo.common.domain.entity

import com.example.pgdemo.common.domain.`enum`.TenantType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator

@Entity
@Table(name = "api_key")
open class ApiKey {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", nullable = false, length = 20)
    lateinit var tenantType: TenantType

    @Column(name = "tenant_id")
    var tenantId: UUID? = null

    @Column(name = "name", nullable = false, length = 100)
    lateinit var name: String

    // Store hash only, never store plaintext key.
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    lateinit var keyHash: String

    @Column(name = "key_prefix", nullable = false, length = 16)
    lateinit var keyPrefix: String

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
}
