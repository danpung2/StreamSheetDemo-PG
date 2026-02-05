package com.example.pgdemo.admin.dto

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateAdminUserRequest(
    @field:NotBlank
    @field:Email
    val email: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val name: String,
    val tenantType: TenantType,
    val tenantId: UUID?,
    val role: UserRole
)

data class CreateAdminUserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val tenantType: TenantType,
    val tenantId: UUID?,
    val role: UserRole,
    val status: String,
    val createdAt: Instant
)

data class CreateApiKeyRequest(
    val tenantType: TenantType,
    val tenantId: UUID?,
    val name: String?
)

data class CreateApiKeyResponse(
    val id: UUID,
    val tenantType: TenantType,
    val tenantId: UUID?,
    val name: String,
    val keyPrefix: String,
    val apiKey: String,
    val createdAt: Instant
)
