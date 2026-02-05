package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.ApiKey
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByKeyHashAndRevokedAtIsNull(keyHash: String): ApiKey?

    @Query("""
        SELECT a FROM ApiKey a
        WHERE a.tenantType = :tenantType
          AND ((:tenantId IS NULL AND a.tenantId IS NULL) OR a.tenantId = :tenantId)
          AND a.revokedAt IS NULL
    """)
    fun findActiveByTenant(
        @Param("tenantType") tenantType: com.example.pgdemo.common.domain.`enum`.TenantType,
        @Param("tenantId") tenantId: UUID?
    ): List<ApiKey>
}
