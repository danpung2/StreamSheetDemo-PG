package com.example.pgdemo.admin.controller

import com.example.pgdemo.admin.dto.LoginRequest
import com.example.pgdemo.admin.dto.RefreshRequest
import com.example.pgdemo.admin.dto.TokenResponse
import com.example.pgdemo.admin.service.AuthService
import com.example.pgdemo.common.domain.entity.AdminUser
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): TokenResponse {
        return authService.login(request.email, request.password)
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): TokenResponse {
        return authService.refresh(request.refreshToken)
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication?): ResponseEntity<Void> {
        val adminUser = authentication?.principal as? AdminUser
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")

        val adminUserId = adminUser.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        authService.logout(adminUserId)
        return ResponseEntity.noContent().build()
    }
}
