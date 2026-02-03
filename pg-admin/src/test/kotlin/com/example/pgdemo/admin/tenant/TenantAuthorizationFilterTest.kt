package com.example.pgdemo.admin.tenant

import com.example.pgdemo.common.domain.`enum`.TenantType
import com.example.pgdemo.common.domain.`enum`.UserRole
import com.example.pgdemo.common.domain.entity.AdminUser
import jakarta.servlet.FilterChain
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

@DisplayName("테넌트 권한 필터 테스트")
class TenantAuthorizationFilterTest {

    private val filter = TenantAuthorizationFilter()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        TenantContext.clear()
    }

    @Test
    @DisplayName("인증된 관리자 사용자에 대해 테넌트 컨텍스트를 설정하고 체인 실행 후 초기화한다")
    fun setsTenantContextForAuthenticatedAdminUserAndClearsAfterChain() {
        val adminUser = buildAdminUser(
            tenantType = TenantType.HEADQUARTERS,
            role = UserRole.ADMIN,
            tenantId = UUID.randomUUID()
        )
        val authentication = UsernamePasswordAuthenticationToken(adminUser, "N/A", emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        var observed: TenantInfo? = null
        val chain = FilterChain { _, _ ->
            observed = TenantContext.get()
        }

        val request = MockHttpServletRequest("GET", "/admin/test")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        assertNotNull(observed)
        assertEquals(adminUser.id, observed?.userId)
        assertEquals(adminUser.tenantType, observed?.tenantType)
        assertEquals(adminUser.tenantId, observed?.tenantId)
        assertEquals(adminUser.role, observed?.role)
        assertNull(TenantContext.get())
    }

    @Test
    @DisplayName("체인 실행 중 예외 발생 시 테넌트 컨텍스트를 초기화한다")
    fun clearsTenantContextWhenChainThrows() {
        val adminUser = buildAdminUser(
            tenantType = TenantType.OPERATOR,
            role = UserRole.MANAGER,
            tenantId = null
        )
        val authentication = UsernamePasswordAuthenticationToken(adminUser, "N/A", emptyList())
        SecurityContextHolder.getContext().authentication = authentication

        val chain = FilterChain { _, _ ->
            throw IllegalStateException("boom")
        }

        val request = MockHttpServletRequest("GET", "/admin/fail")
        val response = MockHttpServletResponse()

        assertFailsWith<IllegalStateException> {
            filter.doFilter(request, response, chain)
        }

        assertNull(TenantContext.get())
    }

    private fun buildAdminUser(tenantType: TenantType, role: UserRole, tenantId: UUID?): AdminUser {
        val user = AdminUser()
        user.id = UUID.randomUUID()
        user.email = "admin@example.com"
        user.passwordHash = "hash"
        user.name = "Admin"
        user.tenantType = tenantType
        user.tenantId = tenantId
        user.role = role
        return user
    }
}
