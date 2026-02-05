package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import org.springframework.stereotype.Component

/**
 * 테넌트 권한 매트릭스
 * Tenant Permission Matrix
 *
 * NOTE: 리소스별 테넌트 유형과 역할에 따른 권한을 정의합니다.
 *       Defines permissions based on tenant type and role for each resource.
 */
@Component
class TenantPermissionMatrix {

    /**
     * 권한 정의
     * Permission definitions
     */
    enum class Permission {
        /** 읽기 권한 / Read permission */
        READ,
        /** 쓰기 권한 / Write permission */
        WRITE,
        /** 삭제 권한 / Delete permission */
        DELETE,
        /** 내보내기 권한 / Export permission */
        EXPORT
    }

    /**
     * 리소스 유형
     * Resource types
     */
    enum class Resource {
        /** 본사 관리 / Headquarters management */
        HEADQUARTERS,
        /** 가맹점 관리 / Merchant management */
        MERCHANT,
        /** 결제 내역 / Payment transactions */
        PAYMENT,
        /** 환불 내역 / Refund transactions */
        REFUND,
        /** 사용자 관리 / User management */
        USER,
        /** 시스템 설정 / System settings */
        SYSTEM
    }

    /**
     * 권한 매트릭스 정의
     * Permission matrix definition
     *
     * 구조: Resource -> TenantType -> UserRole -> Set<Permission>
     * Structure: Resource -> TenantType -> UserRole -> Set<Permission>
     */
    private val permissionMatrix: Map<Resource, Map<TenantType, Map<UserRole, Set<Permission>>>> = mapOf(
        // 본사 리소스 권한
        // Headquarters resource permissions
        Resource.HEADQUARTERS to mapOf(
            TenantType.OPERATOR to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.DELETE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.HEADQUARTERS to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.MERCHANT to mapOf(
                UserRole.ADMIN to setOf(),
                UserRole.MANAGER to setOf(),
                UserRole.VIEWER to setOf()
            )
        ),

        // 가맹점 리소스 권한
        // Merchant resource permissions
        Resource.MERCHANT to mapOf(
            TenantType.OPERATOR to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.DELETE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.HEADQUARTERS to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.MERCHANT to mapOf(
                UserRole.ADMIN to setOf(Permission.READ),
                UserRole.MANAGER to setOf(Permission.READ),
                UserRole.VIEWER to setOf(Permission.READ)
            )
        ),

        // 결제 리소스 권한
        // Payment resource permissions
        Resource.PAYMENT to mapOf(
            TenantType.OPERATOR to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.HEADQUARTERS to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.MERCHANT to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ),
                UserRole.VIEWER to setOf(Permission.READ)
            )
        ),

        // 환불 리소스 권한
        // Refund resource permissions
        Resource.REFUND to mapOf(
            TenantType.OPERATOR to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.HEADQUARTERS to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ, Permission.EXPORT),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.MERCHANT to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.EXPORT),
                UserRole.MANAGER to setOf(Permission.READ),
                UserRole.VIEWER to setOf(Permission.READ)
            )
        ),

        // 사용자 관리 권한
        // User management permissions
        Resource.USER to mapOf(
            TenantType.OPERATOR to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE, Permission.DELETE),
                UserRole.MANAGER to setOf(Permission.READ, Permission.WRITE),
                UserRole.VIEWER to setOf(Permission.READ)
            ),
            TenantType.HEADQUARTERS to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE),
                UserRole.MANAGER to setOf(Permission.READ),
                UserRole.VIEWER to setOf()
            ),
            TenantType.MERCHANT to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE),
                UserRole.MANAGER to setOf(),
                UserRole.VIEWER to setOf()
            )
        ),

        // 시스템 설정 권한
        // System settings permissions
        Resource.SYSTEM to mapOf(
            TenantType.OPERATOR to mapOf(
                UserRole.ADMIN to setOf(Permission.READ, Permission.WRITE),
                UserRole.MANAGER to setOf(Permission.READ),
                UserRole.VIEWER to setOf()
            ),
            TenantType.HEADQUARTERS to mapOf(
                UserRole.ADMIN to setOf(),
                UserRole.MANAGER to setOf(),
                UserRole.VIEWER to setOf()
            ),
            TenantType.MERCHANT to mapOf(
                UserRole.ADMIN to setOf(),
                UserRole.MANAGER to setOf(),
                UserRole.VIEWER to setOf()
            )
        )
    )

    /**
     * 특정 리소스에 대한 권한 확인
     * Check permission for a specific resource
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @param resource 리소스 유형 / Resource type
     * @param permission 확인할 권한 / Permission to check
     * @return 권한 보유 여부 / Whether the permission is granted
     */
    fun hasPermission(tenantInfo: TenantInfo, resource: Resource, permission: Permission): Boolean {
        val permissions = getPermissions(tenantInfo, resource)
        return permission in permissions
    }

    /**
     * 특정 리소스에 대한 모든 권한 조회
     * Get all permissions for a specific resource
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @param resource 리소스 유형 / Resource type
     * @return 권한 집합 / Set of permissions
     */
    fun getPermissions(tenantInfo: TenantInfo, resource: Resource): Set<Permission> {
        return permissionMatrix[resource]
            ?.get(tenantInfo.tenantType)
            ?.get(tenantInfo.role)
            ?: emptySet()
    }

    /**
     * 권한 검증 (예외 발생)
     * Validate permission (throws exception)
     *
     * @param tenantInfo 테넌트 정보 / Tenant information
     * @param resource 리소스 유형 / Resource type
     * @param permission 필요한 권한 / Required permission
     * @throws TenantAccessDeniedException 권한이 없을 경우 / If permission is denied
     */
    fun requirePermission(tenantInfo: TenantInfo, resource: Resource, permission: Permission) {
        if (!hasPermission(tenantInfo, resource, permission)) {
            throw TenantAccessDeniedException(
                "Permission denied: ${tenantInfo.tenantType}/${tenantInfo.role} " +
                "cannot ${permission.name} ${resource.name}"
            )
        }
    }

    /**
     * 읽기 권한 검증
     * Validate read permission
     */
    fun requireRead(tenantInfo: TenantInfo, resource: Resource) {
        requirePermission(tenantInfo, resource, Permission.READ)
    }

    /**
     * 쓰기 권한 검증
     * Validate write permission
     */
    fun requireWrite(tenantInfo: TenantInfo, resource: Resource) {
        requirePermission(tenantInfo, resource, Permission.WRITE)
    }

    /**
     * 삭제 권한 검증
     * Validate delete permission
     */
    fun requireDelete(tenantInfo: TenantInfo, resource: Resource) {
        requirePermission(tenantInfo, resource, Permission.DELETE)
    }

    /**
     * 내보내기 권한 검증
     * Validate export permission
     */
    fun requireExport(tenantInfo: TenantInfo, resource: Resource) {
        requirePermission(tenantInfo, resource, Permission.EXPORT)
    }
}
