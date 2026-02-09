package com.example.pgdemo.admin.security

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * In-memory rate limiter for login attempts.
 * 로그인 시도에 대한 인메모리 Rate Limiter.
 *
 * Uses a sliding window algorithm to track and limit login attempts per IP address.
 * IP 주소별 로그인 시도를 추적하고 제한하기 위해 슬라이딩 윈도우 알고리즘을 사용합니다.
 */
@Component
class LoginRateLimiter(
    private val rateLimitProperties: RateLimitProperties
) {
    private val logger = LoggerFactory.getLogger(LoginRateLimiter::class.java)

    /**
     * Stores login attempt timestamps for each IP address.
     * 각 IP 주소별 로그인 시도 타임스탬프를 저장합니다.
     */
    private val attemptsByKey = ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>>()

    /**
     * Stores block expiration time for each IP address.
     * 각 IP 주소별 차단 만료 시간을 저장합니다.
     */
    private val blockedKeys = ConcurrentHashMap<String, Instant>()

    /**
     * Check if the IP is allowed to make a login attempt.
     * IP가 로그인 시도를 할 수 있는지 확인합니다.
     *
     * @param ipAddress the client IP address / 클라이언트 IP 주소
     * @return true if allowed, false if rate limited / 허용되면 true, 제한되면 false
     */
    fun isAllowed(bucket: RateLimitProperties.Bucket, ipAddress: String): Boolean {
        if (!rateLimitProperties.enabled) {
            return true
        }

        val now = Instant.now()

        val key = key(bucket, ipAddress)

        // Check if IP is blocked / IP 차단 여부 확인
        val blockExpiry = blockedKeys[key]
        if (blockExpiry != null) {
            if (now.isBefore(blockExpiry)) {
                logger.debug("{}:{} is blocked until {}", bucket, ipAddress, blockExpiry)
                return false
            } else {
                // Block expired, remove it / 차단 만료, 제거
                blockedKeys.remove(key)
            }
        }

        return true
    }

    /**
     * Record a login attempt for the given IP address.
     * 주어진 IP 주소에 대한 로그인 시도를 기록합니다.
     *
     * @param ipAddress the client IP address / 클라이언트 IP 주소
     * @return true if within limit, false if limit exceeded (IP will be blocked)
     *         제한 내이면 true, 제한 초과 시 false (IP 차단됨)
     */
    fun recordAttempt(bucket: RateLimitProperties.Bucket, ipAddress: String): Boolean {
        if (!rateLimitProperties.enabled) {
            return true
        }

        val now = Instant.now()
        val windowDuration = rateLimitProperties.getWindowDuration(bucket)
        val windowStart = now.minus(windowDuration)

        val maxAttempts = rateLimitProperties.getMaxAttempts(bucket)
        val blockDuration = rateLimitProperties.getBlockDuration(bucket)

        val key = key(bucket, ipAddress)

        // Get or create attempt list for this IP / IP에 대한 시도 목록 가져오기 또는 생성
        val attempts = attemptsByKey.computeIfAbsent(key) { CopyOnWriteArrayList() }

        // Remove old attempts outside the window / 윈도우 외부의 오래된 시도 제거
        attempts.removeIf { it.isBefore(windowStart) }

        // Check if limit exceeded / 제한 초과 여부 확인
        if (attempts.size >= maxAttempts) {
            // Block the IP / IP 차단
            val blockUntil = now.plus(blockDuration)
            blockedKeys[key] = blockUntil
            logger.warn(
                "Rate limit exceeded for {}:{}. Blocked until {}. Attempts in window: {}",
                bucket,
                ipAddress,
                blockUntil,
                attempts.size
            )
            return false
        }

        // Record the attempt / 시도 기록
        attempts.add(now)
        return true
    }

    /**
     * Get remaining attempts for the given IP address.
     * 주어진 IP 주소의 남은 시도 횟수를 반환합니다.
     *
     * @param ipAddress the client IP address / 클라이언트 IP 주소
     * @return remaining attempts / 남은 시도 횟수
     */
    fun getRemainingAttempts(ipAddress: String): Int {
        if (!rateLimitProperties.enabled) {
            return Int.MAX_VALUE
        }

        return getRemainingAttempts(RateLimitProperties.Bucket.LOGIN, ipAddress)
    }

    fun getRemainingAttempts(bucket: RateLimitProperties.Bucket, ipAddress: String): Int {
        if (!rateLimitProperties.enabled) {
            return Int.MAX_VALUE
        }

        val now = Instant.now()
        val windowStart = now.minus(rateLimitProperties.getWindowDuration(bucket))

        val key = key(bucket, ipAddress)
        val attempts = attemptsByKey[key] ?: return rateLimitProperties.getMaxAttempts(bucket)
        val recentAttempts = attempts.count { it.isAfter(windowStart) }

        return maxOf(0, rateLimitProperties.getMaxAttempts(bucket) - recentAttempts)
    }

    /**
     * Get the time when the block expires for the given IP address.
     * 주어진 IP 주소의 차단 만료 시간을 반환합니다.
     *
     * @param ipAddress the client IP address / 클라이언트 IP 주소
     * @return block expiry time, or null if not blocked / 차단 만료 시간, 차단되지 않았으면 null
     */
    fun getBlockExpiry(bucket: RateLimitProperties.Bucket, ipAddress: String): Instant? {
        val blockExpiry = blockedKeys[key(bucket, ipAddress)] ?: return null
        return if (Instant.now().isBefore(blockExpiry)) blockExpiry else null
    }

    /**
     * Cleanup old entries to prevent memory leaks.
     * 메모리 누수를 방지하기 위해 오래된 항목을 정리합니다.
     *
     * Runs every 5 minutes.
     * 5분마다 실행됩니다.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    fun cleanup() {
        val now = Instant.now()
        val maxWindow = listOf(
            rateLimitProperties.windowDuration,
            rateLimitProperties.demoWindowDuration,
            rateLimitProperties.exportWindowDuration
        ).maxOrNull() ?: rateLimitProperties.windowDuration
        val windowStart = now.minus(maxWindow)

        // Cleanup expired blocks / 만료된 차단 정리
        blockedKeys.entries.removeIf { it.value.isBefore(now) }

        // Cleanup old attempts / 오래된 시도 정리
        attemptsByKey.forEach { (_, attempts) ->
            attempts.removeIf { it.isBefore(windowStart) }
        }

        // Remove empty entries / 빈 항목 제거
        attemptsByKey.entries.removeIf { it.value.isEmpty() }

        if (attemptsByKey.isNotEmpty() || blockedKeys.isNotEmpty()) {
            logger.debug(
                "Rate limiter cleanup: {} keys tracked, {} keys blocked",
                attemptsByKey.size,
                blockedKeys.size
            )
        }
    }

    private fun key(bucket: RateLimitProperties.Bucket, ipAddress: String): String {
        return "${bucket.name}:$ipAddress"
    }
}
