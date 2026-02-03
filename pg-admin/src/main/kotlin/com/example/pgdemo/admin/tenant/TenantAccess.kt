package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole

/**
 * 테넌트 접근 권한 검증 어노테이션
 * Annotation for tenant access authorization
 *
 * NOTE: 이 어노테이션이 적용된 메서드는 TenantAccessAspect에 의해 권한이 검증됩니다.
 *       Methods annotated with this will have their access verified by TenantAccessAspect.
 *
 * @param allowedTenantTypes 허용된 테넌트 유형 목록 (기본값: 모든 유형)
 *                            List of allowed tenant types (default: all types)
 * @param allowedRoles 허용된 역할 목록 (기본값: 모든 역할)
 *                      List of allowed roles (default: all roles)
 * @param requireTenantId 테넌트 ID가 필수인지 여부 (기본값: false)
 *                         Whether tenant ID is required (default: false)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class TenantAccess(
    /**
     * 허용된 테넌트 유형 (비어있으면 모든 유형 허용)
     * Allowed tenant types (empty means all types are allowed)
     */
    val allowedTenantTypes: Array<TenantType> = [],
    
    /**
     * 허용된 역할 (비어있으면 모든 역할 허용)
     * Allowed roles (empty means all roles are allowed)
     */
    val allowedRoles: Array<UserRole> = [],
    
    /**
     * 테넌트 ID 필수 여부 (OPERATOR 이외의 경우)
     * Whether tenant ID is required (for non-OPERATOR types)
     */
    val requireTenantId: Boolean = false
)

/**
 * 운영자 전용 접근 검증 어노테이션
 * Annotation for operator-only access
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@TenantAccess(allowedTenantTypes = [TenantType.OPERATOR])
annotation class OperatorOnly

/**
 * 운영자 또는 본사 접근 검증 어노테이션
 * Annotation for operator or headquarters access
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@TenantAccess(allowedTenantTypes = [TenantType.OPERATOR, TenantType.HEADQUARTERS])
annotation class OperatorOrHeadquarters

/**
 * 관리자 역할 필수 검증 어노테이션
 * Annotation requiring admin role
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@TenantAccess(allowedRoles = [UserRole.ADMIN])
annotation class AdminOnly

/**
 * 관리자 또는 매니저 역할 필수 검증 어노테이션
 * Annotation requiring admin or manager role
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@TenantAccess(allowedRoles = [UserRole.ADMIN, UserRole.MANAGER])
annotation class ManagerOrAbove
