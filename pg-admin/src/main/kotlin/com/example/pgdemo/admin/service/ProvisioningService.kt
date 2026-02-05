package com.example.pgdemo.admin.service

import com.example.pgdemo.admin.dto.CreateAdminUserRequest
import com.example.pgdemo.admin.dto.CreateAdminUserResponse
import com.example.pgdemo.admin.dto.CreateApiKeyRequest
import com.example.pgdemo.admin.dto.CreateApiKeyResponse
import com.example.pgdemo.admin.tenant.TenantAccessDeniedException
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.entity.ApiKey
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import com.example.pgdemo.common.domain.repository.ApiKeyRepository
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ProvisioningService(
    private val adminUserRepository: AdminUserRepository,
    private val headquartersRepository: HeadquartersRepository,
    private val merchantRepository: MerchantRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun createAdminUser(request: CreateAdminUserRequest): CreateAdminUserResponse {
        val caller = TenantContext.require()

        val email = request.email.trim().lowercase()
        require(email.isNotBlank()) { "email is required" }
        require(request.password.isNotBlank()) { "password is required" }
        require(request.name.isNotBlank()) { "name is required" }

        val role = request.role
        require(role == UserRole.ADMIN || role == UserRole.MANAGER) {
            "role must be ADMIN or MANAGER"
        }

        val tenantType = request.tenantType
        val tenantId = request.tenantId

        when (tenantType) {
            TenantType.OPERATOR -> {
                require(tenantId == null) { "tenantId must be null for OPERATOR" }
                require(role == UserRole.MANAGER) { "OPERATOR user can only be MANAGER" }
            }
            TenantType.HEADQUARTERS -> {
                require(tenantId != null) { "tenantId is required for HEADQUARTERS" }
                require(headquartersRepository.existsById(tenantId)) { "Headquarters not found" }
            }
            TenantType.MERCHANT -> {
                require(tenantId != null) { "tenantId is required for MERCHANT" }
                require(merchantRepository.existsById(tenantId)) { "Merchant not found" }
            }
        }

        enforceUserCreationPermission(
            callerTenantType = caller.tenantType,
            callerRole = caller.role,
            callerTenantId = caller.tenantId,
            targetTenantType = tenantType,
            targetRole = role,
            targetTenantId = tenantId
        )

        if (adminUserRepository.existsByEmail(email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
        }

        val saved = adminUserRepository.save(
            AdminUser().apply {
                this.email = email
                this.passwordHash = passwordEncoder.encode(request.password)
                this.name = request.name.trim()
                this.tenantType = tenantType
                this.tenantId = tenantId
                this.role = role
                this.status = "ACTIVE"
            }
        )

        val id = saved.id ?: throw IllegalStateException("Admin user id is missing")
        return CreateAdminUserResponse(
            id = id,
            email = saved.email,
            name = saved.name,
            tenantType = saved.tenantType,
            tenantId = saved.tenantId,
            role = saved.role,
            status = saved.status,
            createdAt = saved.createdAt
        )
    }

    @Transactional
    fun createApiKey(request: CreateApiKeyRequest): CreateApiKeyResponse {
        val caller = TenantContext.require()
        if (caller.tenantType != TenantType.OPERATOR || caller.role != UserRole.ADMIN) {
            throw TenantAccessDeniedException("Operator admin access required")
        }

        val tenantType = request.tenantType
        val tenantId = request.tenantId

        when (tenantType) {
            TenantType.OPERATOR -> require(tenantId == null) { "tenantId must be null for OPERATOR" }
            TenantType.HEADQUARTERS -> {
                require(tenantId != null) { "tenantId is required for HEADQUARTERS" }
                require(headquartersRepository.existsById(tenantId)) { "Headquarters not found" }
            }
            TenantType.MERCHANT -> {
                require(tenantId != null) { "tenantId is required for MERCHANT" }
                require(merchantRepository.existsById(tenantId)) { "Merchant not found" }
            }
        }

        val name = request.name?.trim().takeUnless { it.isNullOrBlank() }
            ?: "${tenantType.name} API Key"

        val apiKeyPlain = generateApiKeyPlain(tenantType)
        val keyHash = sha256Hex(apiKeyPlain)

        val entity = ApiKey().apply {
            this.tenantType = tenantType
            this.tenantId = tenantId
            this.name = name
            this.keyHash = keyHash
            this.keyPrefix = apiKeyPlain.take(12)
        }

        val saved = apiKeyRepository.save(entity)
        val id = saved.id ?: throw IllegalStateException("ApiKey id is missing")

        return CreateApiKeyResponse(
            id = id,
            tenantType = saved.tenantType,
            tenantId = saved.tenantId,
            name = saved.name,
            keyPrefix = saved.keyPrefix,
            apiKey = apiKeyPlain,
            createdAt = saved.createdAt
        )
    }

    private fun enforceUserCreationPermission(
        callerTenantType: TenantType,
        callerRole: UserRole,
        callerTenantId: UUID?,
        targetTenantType: TenantType,
        targetRole: UserRole,
        targetTenantId: UUID?
    ) {
        if (callerRole != UserRole.ADMIN) {
            throw TenantAccessDeniedException("Admin access required")
        }

        when (callerTenantType) {
            TenantType.OPERATOR -> {
                if (targetTenantType == TenantType.OPERATOR) {
                    if (targetRole != UserRole.MANAGER || targetTenantId != null) {
                        throw TenantAccessDeniedException("Operator can only create operator MANAGER")
                    }
                    return
                }
                if (targetTenantType == TenantType.HEADQUARTERS || targetTenantType == TenantType.MERCHANT) {
                    // Operator admin can create both ADMIN and MANAGER for HQ/Merchant
                    return
                }
            }
            TenantType.HEADQUARTERS -> {
                if (targetTenantType != TenantType.HEADQUARTERS || targetRole != UserRole.MANAGER) {
                    throw TenantAccessDeniedException("Headquarters admin can only create headquarters MANAGER")
                }
                if (callerTenantId == null || targetTenantId != callerTenantId) {
                    throw TenantAccessDeniedException("Headquarters admin can only create users in own headquarters")
                }
                return
            }
            TenantType.MERCHANT -> {
                if (targetTenantType != TenantType.MERCHANT || targetRole != UserRole.MANAGER) {
                    throw TenantAccessDeniedException("Merchant admin can only create merchant MANAGER")
                }
                if (callerTenantId == null || targetTenantId != callerTenantId) {
                    throw TenantAccessDeniedException("Merchant admin can only create users in own merchant")
                }
                return
            }
        }

        throw TenantAccessDeniedException("Access denied")
    }

    private fun generateApiKeyPlain(tenantType: TenantType): String {
        val typeToken = when (tenantType) {
            TenantType.OPERATOR -> "op"
            TenantType.HEADQUARTERS -> "hq"
            TenantType.MERCHANT -> "m"
        }

        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "pgk_${typeToken}_$token"
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
