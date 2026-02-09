package com.example.pgdemo.admin.config

import com.example.pgdemo.admin.security.JwtAuthenticationFilter
import com.example.pgdemo.admin.security.JwtProperties
import com.example.pgdemo.admin.security.LoginProperties
import com.example.pgdemo.admin.tenant.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Spring Security 설정
 * Spring Security configuration
 *
 * NOTE: JWT 인증 및 멀티테넌트 권한 검증을 설정합니다.
 *       Configures JWT authentication and multi-tenant authorization.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(JwtProperties::class, LoginProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val tenantAuthorizationFilter: TenantAuthorizationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, authException ->
                    if (request.requestURI.startsWith("/api")) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.message)
                    } else {
                        response.sendRedirect("/login")
                    }
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/auth/login", 
                    "/api/auth/refresh", 
                    "/actuator/health",
                    "/login",
                    "/",
                    "/css/**", 
                    "/js/**", 
                    "/images/**",
                    "/favicon.ico",
                    "/error"
                ).permitAll()
                    .anyRequest().authenticated()
            }
            // JWT 인증 필터 적용
            // Apply JWT authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // 테넌트 컨텍스트 설정 필터 (JWT 인증 후 실행)
            // Tenant context filter (runs after JWT authentication)
            .addFilterAfter(tenantAuthorizationFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
