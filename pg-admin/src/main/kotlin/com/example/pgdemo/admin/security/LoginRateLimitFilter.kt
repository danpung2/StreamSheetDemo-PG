package com.example.pgdemo.admin.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that applies rate limiting to login endpoints.
 * 로그인 엔드포인트에 Rate Limiting을 적용하는 필터.
 *
 * Protects against brute-force attacks by limiting login attempts per IP address.
 * IP 주소별 로그인 시도를 제한하여 브루트포스 공격을 방지합니다.
 */
@Component
class LoginRateLimitFilter(
    private val rateLimiter: LoginRateLimiter,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(LoginRateLimitFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val bucket = resolveBucket(request)
        if (bucket == null) {
            filterChain.doFilter(request, response)
            return
        }

        val clientIp = getClientIp(request)

        if (!rateLimiter.isAllowed(bucket, clientIp)) {
            handleRateLimitExceeded(request, response, bucket, clientIp)
            return
        }

        if (!rateLimiter.recordAttempt(bucket, clientIp)) {
            handleRateLimitExceeded(request, response, bucket, clientIp)
            return
        }

        addRateLimitHeaders(response, bucket, clientIp)

        filterChain.doFilter(request, response)
    }

    private fun resolveBucket(request: HttpServletRequest): RateLimitProperties.Bucket? {
        val path = request.requestURI
        val method = request.method

        if (path == "/api/demo/start") {
            return RateLimitProperties.Bucket.DEMO
        }
        if (path == "/api/auth/login" || path == "/api/auth/refresh") {
            return RateLimitProperties.Bucket.LOGIN
        }
        if (path == "/admin/exports/payments" && method.equals("GET", ignoreCase = true)) {
            return RateLimitProperties.Bucket.EXPORT_READ
        }
        if (path.startsWith("/admin/exports/") || path == "/admin/exports") {
            return RateLimitProperties.Bucket.EXPORT
        }
        return null
    }

    /**
     * Extract client IP address, considering proxy headers.
     * 프록시 헤더를 고려하여 클라이언트 IP 주소를 추출합니다.
     */
    private fun getClientIp(request: HttpServletRequest): String {
        // Check X-Forwarded-For header (set by proxies/load balancers)
        // X-Forwarded-For 헤더 확인 (프록시/로드밸런서에서 설정)
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            // Take the first IP (original client)
            // 첫 번째 IP 사용 (원래 클라이언트)
            return xForwardedFor.split(",").first().trim()
        }

        // Check X-Real-IP header (set by nginx)
        // X-Real-IP 헤더 확인 (nginx에서 설정)
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp.trim()
        }

        // Fallback to remote address / 기본값으로 원격 주소 사용
        return request.remoteAddr ?: "unknown"
    }

    private fun handleRateLimitExceeded(
        request: HttpServletRequest,
        response: HttpServletResponse,
        bucket: RateLimitProperties.Bucket,
        clientIp: String
    ) {
        log.warn("Rate limit exceeded for {} IP: {}", bucket, clientIp)

        val blockExpiry = rateLimiter.getBlockExpiry(bucket, clientIp)
        val retryAfterSeconds = if (blockExpiry != null) {
            java.time.Duration.between(java.time.Instant.now(), blockExpiry).seconds.coerceAtLeast(1)
        } else {
            60L
        }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.setHeader("X-RateLimit-Remaining", "0")

        val isExportBucket = bucket == RateLimitProperties.Bucket.EXPORT || bucket == RateLimitProperties.Bucket.EXPORT_READ
        if (isExportBucket && isHtmlPageRequest(request)) {
            val encodedReason = URLEncoder.encode(bucket.name, StandardCharsets.UTF_8)
            response.sendRedirect("/admin/exports/payments?rateLimited=true&retryAfter=$retryAfterSeconds&bucket=$encodedReason")
            return
        }

        val errorResponse = mapOf(
            "error" to "Too Many Requests",
            "message" to "Rate limit exceeded. Please try again later.",
            "bucket" to bucket.name,
            "retryAfterSeconds" to retryAfterSeconds
        )

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }

    private fun isHtmlPageRequest(request: HttpServletRequest): Boolean {
        if (request.method.equals("GET", ignoreCase = true).not()) {
            return false
        }
        val accept = request.getHeader("Accept") ?: return false
        return accept.contains(MediaType.TEXT_HTML_VALUE)
    }

    private fun addRateLimitHeaders(
        response: HttpServletResponse,
        bucket: RateLimitProperties.Bucket,
        clientIp: String
    ) {
        val remaining = rateLimiter.getRemainingAttempts(bucket, clientIp)
        response.setHeader("X-RateLimit-Remaining", remaining.toString())
    }
}
