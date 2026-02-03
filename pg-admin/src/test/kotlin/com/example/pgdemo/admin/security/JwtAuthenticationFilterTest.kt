package com.example.pgdemo.admin.security

import com.example.pgdemo.common.domain.entity.AdminUser
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.enum.UserRole
import com.example.pgdemo.common.domain.repository.AdminUserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@ExtendWith(MockitoExtension::class)
@DisplayName("JWT 인증 필터 테스트")
class JwtAuthenticationFilterTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var adminUserRepository: AdminUserRepository
    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        val properties = JwtProperties().apply {
            secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            accessTokenExpiry = Duration.ofMinutes(15)
            refreshTokenExpiry = Duration.ofDays(7)
        }
        jwtTokenProvider = JwtTokenProvider(properties)
        adminUserRepository = mock(AdminUserRepository::class.java)
        filter = JwtAuthenticationFilter(jwtTokenProvider, adminUserRepository)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증을 설정하지 않는다")
    fun `Authorization 헤더가 없으면 인증을 설정하지 않는다`() {
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val filterChain = mock(FilterChain::class.java)

        `when`(request.getHeader("Authorization")).thenReturn(null)

        filter.doFilter(request, response, filterChain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(filterChain).doFilter(request, response)
    }

    @Test
    @DisplayName("Authorization 헤더가 있으면 인증 객체를 설정한다")
    fun `Authorization 헤더가 있으면 인증 객체를 설정한다`() {
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val filterChain = mock(FilterChain::class.java)
        val adminUser = adminUser()
        val adminUserId = requireNotNull(adminUser.id)
        val accessToken = jwtTokenProvider.generateAccessToken(adminUser)

        `when`(request.getHeader("Authorization")).thenReturn("Bearer $accessToken")
        `when`(request.remoteAddr).thenReturn("127.0.0.1")
        `when`(request.getSession(false)).thenReturn(null)
        `when`(adminUserRepository.findById(eq(adminUserId))).thenReturn(Optional.of(adminUser))

        filter.doFilter(request, response, filterChain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertTrue(authentication is UsernamePasswordAuthenticationToken)
        assertEquals(adminUser, authentication?.principal)
        assertTrue(authentication?.authorities?.contains(SimpleGrantedAuthority("ROLE_${adminUser.role.name}")) == true)
        verify(adminUserRepository).findById(eq(adminUserId))
        verify(filterChain).doFilter(request, response)
    }

    private fun adminUser(): AdminUser {
        return AdminUser().apply {
            id = UUID.randomUUID()
            email = "admin@example.com"
            passwordHash = "hashed"
            name = "Admin"
            tenantType = TenantType.OPERATOR
            tenantId = UUID.randomUUID()
            role = UserRole.ADMIN
        }
    }
}
