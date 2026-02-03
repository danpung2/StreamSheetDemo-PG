package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component

/**
 * 테넌트 접근 권한 검증 AOP Aspect
 * AOP Aspect for tenant access authorization
 *
 * NOTE: @TenantAccess 어노테이션이 적용된 메서드/클래스의 접근 권한을 검증합니다.
 *       This aspect verifies access for methods/classes annotated with @TenantAccess.
 */
@Aspect
@Component
class TenantAccessAspect {

    private val log = LoggerFactory.getLogger(TenantAccessAspect::class.java)

    /**
     * @TenantAccess 어노테이션이 적용된 메서드에 대한 권한 검증
     * Authorization for methods annotated with @TenantAccess
     */
    @Around("@annotation(com.example.pgdemo.admin.tenant.TenantAccess) || @within(com.example.pgdemo.admin.tenant.TenantAccess)")
    fun checkTenantAccess(joinPoint: ProceedingJoinPoint): Any? {
        val tenantInfo = TenantContext.require()
        val annotation = findTenantAccessAnnotation(joinPoint)
        
        if (annotation != null) {
            validateAccess(tenantInfo, annotation, joinPoint)
        }
        
        return joinPoint.proceed()
    }

    /**
     * @OperatorOnly 어노테이션이 적용된 메서드에 대한 권한 검증
     * Authorization for methods annotated with @OperatorOnly
     */
    @Around("@annotation(com.example.pgdemo.admin.tenant.OperatorOnly) || @within(com.example.pgdemo.admin.tenant.OperatorOnly)")
    fun checkOperatorOnly(joinPoint: ProceedingJoinPoint): Any? {
        val tenantInfo = TenantContext.require()
        
        if (!tenantInfo.isOperator()) {
            log.warn("Operator-only access denied: userId={}, tenantType={}", 
                tenantInfo.userId, tenantInfo.tenantType)
            throw TenantAccessDeniedException("Operator access required")
        }
        
        return joinPoint.proceed()
    }

    /**
     * @OperatorOrHeadquarters 어노테이션이 적용된 메서드에 대한 권한 검증
     * Authorization for methods annotated with @OperatorOrHeadquarters
     */
    @Around("@annotation(com.example.pgdemo.admin.tenant.OperatorOrHeadquarters) || @within(com.example.pgdemo.admin.tenant.OperatorOrHeadquarters)")
    fun checkOperatorOrHeadquarters(joinPoint: ProceedingJoinPoint): Any? {
        val tenantInfo = TenantContext.require()
        
        if (!tenantInfo.isOperator() && !tenantInfo.isHeadquarters()) {
            log.warn("Operator or Headquarters access denied: userId={}, tenantType={}", 
                tenantInfo.userId, tenantInfo.tenantType)
            throw TenantAccessDeniedException("Operator or Headquarters access required")
        }
        
        return joinPoint.proceed()
    }

    /**
     * @AdminOnly 어노테이션이 적용된 메서드에 대한 권한 검증
     * Authorization for methods annotated with @AdminOnly
     */
    @Around("@annotation(com.example.pgdemo.admin.tenant.AdminOnly) || @within(com.example.pgdemo.admin.tenant.AdminOnly)")
    fun checkAdminOnly(joinPoint: ProceedingJoinPoint): Any? {
        val tenantInfo = TenantContext.require()
        
        if (!tenantInfo.isAdmin()) {
            log.warn("Admin-only access denied: userId={}, role={}", 
                tenantInfo.userId, tenantInfo.role)
            throw TenantAccessDeniedException("Admin access required")
        }
        
        return joinPoint.proceed()
    }

    /**
     * @ManagerOrAbove 어노테이션이 적용된 메서드에 대한 권한 검증
     * Authorization for methods annotated with @ManagerOrAbove
     */
    @Around("@annotation(com.example.pgdemo.admin.tenant.ManagerOrAbove) || @within(com.example.pgdemo.admin.tenant.ManagerOrAbove)")
    fun checkManagerOrAbove(joinPoint: ProceedingJoinPoint): Any? {
        val tenantInfo = TenantContext.require()
        
        if (!tenantInfo.isManagerOrAbove()) {
            log.warn("Manager or above access denied: userId={}, role={}", 
                tenantInfo.userId, tenantInfo.role)
            throw TenantAccessDeniedException("Manager or above access required")
        }
        
        return joinPoint.proceed()
    }

    /**
     * 메서드 또는 클래스에서 @TenantAccess 어노테이션 찾기
     * Find @TenantAccess annotation from method or class
     */
    private fun findTenantAccessAnnotation(joinPoint: ProceedingJoinPoint): TenantAccess? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        
        // 메서드 레벨 어노테이션 우선
        // Method-level annotation takes precedence
        AnnotationUtils.findAnnotation(method, TenantAccess::class.java)?.let { return it }
        
        // 클래스 레벨 어노테이션
        // Class-level annotation
        return AnnotationUtils.findAnnotation(joinPoint.target.javaClass, TenantAccess::class.java)
    }

    /**
     * 접근 권한 검증
     * Validate access
     */
    private fun validateAccess(
        tenantInfo: TenantInfo,
        annotation: TenantAccess,
        joinPoint: ProceedingJoinPoint
    ) {
        // 테넌트 유형 검증
        // Validate tenant type
        val allowedTenantTypes = annotation.allowedTenantTypes
        if (allowedTenantTypes.isNotEmpty() && tenantInfo.tenantType !in allowedTenantTypes) {
            log.warn(
                "Tenant type access denied: userId={}, tenantType={}, allowed={}, method={}",
                tenantInfo.userId,
                tenantInfo.tenantType,
                allowedTenantTypes.toList(),
                joinPoint.signature.name
            )
            throw TenantAccessDeniedException(
                "Access denied for tenant type: ${tenantInfo.tenantType}"
            )
        }

        // 역할 검증
        // Validate role
        val allowedRoles = annotation.allowedRoles
        if (allowedRoles.isNotEmpty() && tenantInfo.role !in allowedRoles) {
            log.warn(
                "Role access denied: userId={}, role={}, allowed={}, method={}",
                tenantInfo.userId,
                tenantInfo.role,
                allowedRoles.toList(),
                joinPoint.signature.name
            )
            throw TenantAccessDeniedException(
                "Access denied for role: ${tenantInfo.role}"
            )
        }

        // 테넌트 ID 필수 검증 (OPERATOR 제외)
        // Validate required tenant ID (except OPERATOR)
        if (annotation.requireTenantId && 
            tenantInfo.tenantType != TenantType.OPERATOR && 
            tenantInfo.tenantId == null) {
            log.warn(
                "Tenant ID required but not set: userId={}, tenantType={}, method={}",
                tenantInfo.userId,
                tenantInfo.tenantType,
                joinPoint.signature.name
            )
            throw TenantAccessDeniedException("Tenant ID is required")
        }
        
        if (log.isDebugEnabled) {
            log.debug(
                "Tenant access granted: userId={}, tenantType={}, role={}, method={}",
                tenantInfo.userId,
                tenantInfo.tenantType,
                tenantInfo.role,
                joinPoint.signature.name
            )
        }
    }
}

/**
 * 테넌트 접근 거부 예외
 * Exception thrown when tenant access is denied
 */
class TenantAccessDeniedException(message: String) : RuntimeException(message)
