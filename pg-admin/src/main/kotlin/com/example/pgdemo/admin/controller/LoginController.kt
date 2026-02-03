package com.example.pgdemo.admin.controller

import com.example.pgdemo.admin.service.AuthService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Controller
class LoginController(
    private val authService: AuthService
) {

    @GetMapping("/login")
    fun loginPage(): String {
        return "login"
    }

    @PostMapping("/login", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun login(
        @RequestParam email: String,
        @RequestParam(required = false) password: String?,
        response: HttpServletResponse
    ): String {
        try {
            if (password.isNullOrBlank()) {
                return "redirect:/login?error=Invalid credentials"
            }
            
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
            
            return "redirect:/admin/dashboard"
        } catch (ex: Exception) {
            val errorMsg = URLEncoder.encode("Login failed: ${ex.message}", StandardCharsets.UTF_8)
            return "redirect:/login?error=$errorMsg"
        }
    }
    
    @GetMapping("/")
    fun root(): String {
        return "redirect:/admin/dashboard"
    }
}
