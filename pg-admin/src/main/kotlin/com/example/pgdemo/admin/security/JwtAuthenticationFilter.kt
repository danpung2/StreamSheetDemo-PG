package com.example.pgdemo.admin.security

import com.example.pgdemo.common.domain.repository.AdminUserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val adminUserRepository: AdminUserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            var token: String? = null
            val header = request.getHeader("Authorization")
            
            if (header != null && header.startsWith("Bearer ")) {
                token = header.removePrefix("Bearer ")
            } else {
                request.cookies?.find { it.name == "accessToken" }?.let {
                    token = it.value
                }
            }

            if (token != null) {
                if (jwtTokenProvider.validateAccessToken(token!!)) {
                    val userId = jwtTokenProvider.getUserId(token!!)
                    val adminUser = adminUserRepository.findById(userId).orElse(null)
                    if (adminUser != null) {
                        val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_${adminUser.role.name}"))
                        if (jwtTokenProvider.isDemoToken(token!!)) {
                            authorities.add(SimpleGrantedAuthority("ROLE_DEMO"))
                        }
                        val authentication = UsernamePasswordAuthenticationToken(
                            adminUser,
                            null,
                            authorities
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
