package com.example.pgdemo.admin.service

import com.example.pgdemo.admin.security.DemoProperties
import com.example.pgdemo.admin.security.JwtTokenProvider
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
open class DemoSessionService(
    private val demoProperties: DemoProperties,
    private val adminUserRepository: AdminUserRepository,
    private val jwtTokenProvider: JwtTokenProvider
) {
    data class DemoAccessToken(
        val accessToken: String,
        val expiresInSeconds: Long
    )

    open fun issueDemoAccessToken(): DemoAccessToken {
        if (!demoProperties.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        val email = demoProperties.userEmail.trim()
        val adminUser = adminUserRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo user is not seeded")

        if (adminUser.tenantType != TenantType.HEADQUARTERS || adminUser.role != UserRole.MANAGER) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo user is misconfigured")
        }
        if (adminUser.status != "ACTIVE") {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo user is not active")
        }

        val expiry = demoProperties.accessTokenExpiry
        val token = jwtTokenProvider.generateAccessToken(
            adminUser = adminUser,
            accessTokenExpiry = expiry,
            additionalClaims = mapOf("demo" to true)
        )
        return DemoAccessToken(
            accessToken = token,
            expiresInSeconds = expiry.seconds
        )
    }
}
