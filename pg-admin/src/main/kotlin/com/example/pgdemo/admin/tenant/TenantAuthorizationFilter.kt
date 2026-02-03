package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.entity.AdminUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청별 테넌트 컨텍스트 설정 필터
 * Filter that sets up tenant context for each request
 *
 * NOTE: JWT 인증 필터 이후에 실행되어야 합니다.
 *       인증된 사용자 정보를 기반으로 TenantContext를 설정합니다.
 *       This filter must run after the JWT authentication filter.
 *       It sets up TenantContext based on authenticated user information.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class TenantAuthorizationFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(TenantAuthorizationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // 인증 정보에서 AdminUser 추출
            // Extract AdminUser from authentication context
            val authentication = SecurityContextHolder.getContext().authentication
            
            if (authentication != null && authentication.isAuthenticated) {
                val principal = authentication.principal
                
                if (principal is AdminUser) {
                    // 테넌트 정보 생성 및 컨텍스트 설정
                    // Create tenant info and set context
                    val tenantInfo = TenantInfo(
                        userId = principal.id ?: throw IllegalStateException("User ID is required"),
                        tenantType = principal.tenantType,
                        tenantId = principal.tenantId,
                        role = principal.role
                    )
                    
                    TenantContext.set(tenantInfo)
                    
                    if (log.isDebugEnabled) {
                        log.debug(
                            "Tenant context set: userId={}, tenantType={}, tenantId={}, role={}",
                            tenantInfo.userId,
                            tenantInfo.tenantType,
                            tenantInfo.tenantId,
                            tenantInfo.role
                        )
                    }
                }
            }
            
            filterChain.doFilter(request, response)
        } finally {
            // 요청 처리 완료 후 반드시 컨텍스트 정리
            // Always clear context after request processing
            TenantContext.clear()
            
            if (log.isTraceEnabled) {
                log.trace("Tenant context cleared for request: {} {}", request.method, request.requestURI)
            }
        }
    }
}
