package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("테넌트 권한 매트릭스 테스트")
class TenantPermissionMatrixTest {

    private val matrix = TenantPermissionMatrix()

    @Test
    @DisplayName("운영자(Operator)의 내보내기 권한 허용 및 거부 확인")
    fun operatorExportPermissionAllowAndDeny() {
        val operatorAdmin = tenantInfo(TenantType.OPERATOR, UserRole.ADMIN, null)
        val operatorViewer = tenantInfo(TenantType.OPERATOR, UserRole.VIEWER, null)

        assertTrue(
            matrix.hasPermission(
                operatorAdmin,
                TenantPermissionMatrix.Resource.HEADQUARTERS,
                TenantPermissionMatrix.Permission.EXPORT
            )
        )
        assertFalse(
            matrix.hasPermission(
                operatorViewer,
                TenantPermissionMatrix.Resource.PAYMENT,
                TenantPermissionMatrix.Permission.EXPORT
            )
        )
    }

    @Test
    @DisplayName("본사(Headquarters)의 내보내기 권한 허용 및 거부 확인")
    fun headquartersExportPermissionAllowAndDeny() {
        val headquartersManager = tenantInfo(TenantType.HEADQUARTERS, UserRole.MANAGER, UUID.randomUUID())
        val headquartersAdmin = tenantInfo(TenantType.HEADQUARTERS, UserRole.ADMIN, UUID.randomUUID())

        assertTrue(
            matrix.hasPermission(
                headquartersManager,
                TenantPermissionMatrix.Resource.MERCHANT,
                TenantPermissionMatrix.Permission.EXPORT
            )
        )
        assertFalse(
            matrix.hasPermission(
                headquartersAdmin,
                TenantPermissionMatrix.Resource.SYSTEM,
                TenantPermissionMatrix.Permission.EXPORT
            )
        )
    }

    @Test
    @DisplayName("가맹점(Merchant)의 내보내기 권한 허용 및 거부 확인")
    fun merchantExportPermissionAllowAndDeny() {
        val merchantAdmin = tenantInfo(TenantType.MERCHANT, UserRole.ADMIN, UUID.randomUUID())
        val merchantManager = tenantInfo(TenantType.MERCHANT, UserRole.MANAGER, UUID.randomUUID())

        assertTrue(
            matrix.hasPermission(
                merchantAdmin,
                TenantPermissionMatrix.Resource.PAYMENT,
                TenantPermissionMatrix.Permission.EXPORT
            )
        )
        assertFalse(
            matrix.hasPermission(
                merchantManager,
                TenantPermissionMatrix.Resource.PAYMENT,
                TenantPermissionMatrix.Permission.EXPORT
            )
        )
    }

    @Test
    @DisplayName("가맹점(Merchant) 관리자(Admin)는 사용자 생성(WRITE) 권한을 가진다")
    fun merchantAdminCanWriteUserResource() {
        val merchantAdmin = tenantInfo(TenantType.MERCHANT, UserRole.ADMIN, UUID.randomUUID())

        assertTrue(
            matrix.hasPermission(
                merchantAdmin,
                TenantPermissionMatrix.Resource.USER,
                TenantPermissionMatrix.Permission.WRITE
            )
        )
    }

    private fun tenantInfo(tenantType: TenantType, role: UserRole, tenantId: UUID?): TenantInfo {
        return TenantInfo(
            userId = UUID.randomUUID(),
            tenantType = tenantType,
            tenantId = tenantId,
            role = role
        )
    }
}
