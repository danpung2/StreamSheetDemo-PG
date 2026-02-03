package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("테넌트 컨텍스트 테스트")
class TenantContextTest {

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    @DisplayName("값 설정, 조회, 필수 조회 및 설정 여부 확인")
    fun setGetRequireAndIsSet() {
        val tenantInfo = TenantInfo(
            userId = UUID.randomUUID(),
            tenantType = TenantType.OPERATOR,
            tenantId = null,
            role = UserRole.ADMIN
        )

        assertFalse(TenantContext.isSet())

        TenantContext.set(tenantInfo)

        assertTrue(TenantContext.isSet())
        assertEquals(tenantInfo, TenantContext.get())
        assertEquals(tenantInfo, TenantContext.require())
    }

    @Test
    @DisplayName("초기화 시 컨텍스트 제거 및 필수 조회 시 예외 발생 확인")
    fun clearRemovesContextAndRequireThrows() {
        val tenantInfo = TenantInfo(
            userId = UUID.randomUUID(),
            tenantType = TenantType.HEADQUARTERS,
            tenantId = UUID.randomUUID(),
            role = UserRole.MANAGER
        )

        TenantContext.set(tenantInfo)
        TenantContext.clear()

        assertFalse(TenantContext.isSet())
        assertNull(TenantContext.get())
        assertFailsWith<TenantNotFoundException> { TenantContext.require() }
    }
}
