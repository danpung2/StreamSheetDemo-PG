package com.example.pgdemo.admin.controller

import com.example.pgdemo.admin.security.DemoProperties
import com.example.pgdemo.admin.service.DemoSessionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class DemoController(
    private val demoProperties: DemoProperties,
    private val demoSessionService: DemoSessionService
) {
    @GetMapping("/demo")
    fun demo(model: Model): String {
        model.addAttribute("demoEnabled", demoProperties.enabled)
        return "demo"
    }

    @PostMapping("/api/demo/start")
    fun start(request: HttpServletRequest, response: HttpServletResponse): String {
        val demoToken = demoSessionService.issueDemoAccessToken()

        val secure = demoProperties.cookieSecure
            ?: isSecureRequest(request)

        val cookie = ResponseCookie.from("accessToken", demoToken.accessToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite(demoProperties.cookieSameSite)
            .path("/")
            .maxAge(Duration.ofSeconds(demoToken.expiresInSeconds))
            .build()
        response.addHeader("Set-Cookie", cookie.toString())

        return "redirect:/admin/dashboard"
    }

    private fun isSecureRequest(request: HttpServletRequest): Boolean {
        if (request.isSecure) return true
        val forwardedProto = request.getHeader("X-Forwarded-Proto")?.trim()?.lowercase()
        return forwardedProto == "https"
    }
}
