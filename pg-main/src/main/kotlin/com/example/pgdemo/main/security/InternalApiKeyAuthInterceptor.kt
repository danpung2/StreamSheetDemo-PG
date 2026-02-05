package com.example.pgdemo.main.security

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.repository.ApiKeyRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class InternalApiKeyAuthInterceptor(
    private val apiKeyRepository: ApiKeyRepository
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val apiKeyPlain = request.getHeader("X-API-KEY")?.trim()
        if (apiKeyPlain.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-KEY")
            return false
        }

        val apiKey = apiKeyRepository.findByKeyHashAndRevokedAtIsNull(sha256Hex(apiKeyPlain))
        if (apiKey == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid X-API-KEY")
            return false
        }

        if (apiKey.tenantType != TenantType.OPERATOR) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Operator API key required")
            return false
        }

        return true
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
