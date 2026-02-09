package com.example.pgdemo.admin.controller

import com.example.pgdemo.admin.service.AuthService
import com.example.pgdemo.common.domain.entity.AdminUser
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestParam
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.security.core.Authentication

@Controller
class LoginController(
    private val authService: AuthService
) {

    @GetMapping("/login")
    fun loginPage(
        @CookieValue(name = "loginError", required = false) loginError: String?,
        model: Model,
        response: HttpServletResponse
    ): String {
        if (!loginError.isNullOrBlank()) {
            model.addAttribute("loginError", URLDecoder.decode(loginError, StandardCharsets.UTF_8))
            response.addCookie(Cookie("loginError", "").apply {
                path = "/login"
                maxAge = 0
            })
        }
        return "login"
    }

    @PostMapping("/login", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun login(
        @RequestParam email: String,
        @RequestParam(required = false) password: String?,
        response: HttpServletResponse
    ): String {
        if (password.isNullOrBlank()) {
            response.addCookie(Cookie("loginError", URLEncoder.encode("Invalid email or password.", StandardCharsets.UTF_8)).apply {
                path = "/login"
                maxAge = 15
                isHttpOnly = true
            })
            return "redirect:/login"
        }

        return try {
            val tokenResponse = authService.login(email, password)

            // Note: In production, consider Secure flag and SameSite
            val accessTokenCookie = Cookie("accessToken", tokenResponse.accessToken).apply {
                path = "/"
                isHttpOnly = true
                maxAge = 60 * 60 * 24 // 1 day
            }
            response.addCookie(accessTokenCookie)

            // Set Refresh Token Cookie
            val refreshTokenCookie = Cookie("refreshToken", tokenResponse.refreshToken).apply {
                path = "/"
                isHttpOnly = true
                maxAge = 60 * 60 * 24 * 7 // 7 days
            }
            response.addCookie(refreshTokenCookie)

            "redirect:/admin/dashboard"
        } catch (ex: Exception) {
            response.addCookie(Cookie("loginError", URLEncoder.encode("Login failed. Please try again.", StandardCharsets.UTF_8)).apply {
                path = "/login"
                maxAge = 15
                isHttpOnly = true
            })
            "redirect:/login"
        }
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication?, response: HttpServletResponse): String {
        val adminUser = authentication?.principal as? AdminUser
        adminUser?.id?.let { authService.logout(it) }

        response.addCookie(Cookie("accessToken", "").apply {
            path = "/"
            maxAge = 0
            isHttpOnly = true
        })
        response.addCookie(Cookie("refreshToken", "").apply {
            path = "/"
            maxAge = 0
            isHttpOnly = true
        })
        return "redirect:/login"
    }
    
    @GetMapping("/")
    fun root(): String {
        return "redirect:/demo"
    }
}
