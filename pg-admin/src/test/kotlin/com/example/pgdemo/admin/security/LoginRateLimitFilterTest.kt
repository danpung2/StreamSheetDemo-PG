package com.example.pgdemo.admin.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("로그인 Rate Limit 필터 테스트")
class LoginRateLimitFilterTest {
    private val terminalServlet = object : HttpServlet() {
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
            resp.status = HttpServletResponse.SC_OK
        }
    }

    @Test
    @DisplayName("HTML export 페이지 요청이 제한되면 안내 파라미터와 함께 리다이렉트한다")
    fun `HTML export 페이지 요청이 제한되면 안내 파라미터와 함께 리다이렉트한다`() {
        val props = RateLimitProperties().apply {
            enabled = true
            exportReadMaxAttempts = 0
        }
        val filter = LoginRateLimitFilter(LoginRateLimiter(props), ObjectMapper())

        val request = MockHttpServletRequest("GET", "/admin/exports/payments").apply {
            addHeader("Accept", "text/html")
            remoteAddr = "127.0.0.1"
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain(terminalServlet))

        assertThat(response.redirectedUrl).contains("/admin/exports/payments?rateLimited=true")
        assertThat(response.redirectedUrl).contains("bucket=EXPORT_READ")
    }

    @Test
    @DisplayName("JSON export 요청이 제한되면 429 JSON 계약을 유지한다")
    fun `JSON export 요청이 제한되면 429 JSON 계약을 유지한다`() {
        val props = RateLimitProperties().apply {
            enabled = true
            exportReadMaxAttempts = 0
        }
        val filter = LoginRateLimitFilter(LoginRateLimiter(props), ObjectMapper())

        val request = MockHttpServletRequest("GET", "/admin/exports/payments").apply {
            addHeader("Accept", "application/json")
            remoteAddr = "127.0.0.1"
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain(terminalServlet))

        assertThat(response.status).isEqualTo(429)
        assertThat(response.contentType).isEqualTo("application/json")
        assertThat(response.contentAsString).contains("Too Many Requests")
        assertThat(response.contentAsString).contains("EXPORT_READ")
    }

    @Test
    @DisplayName("export 조회와 고비용 엔드포인트는 서로 다른 버킷을 사용한다")
    fun `export 조회와 고비용 엔드포인트는 서로 다른 버킷을 사용한다`() {
        val props = RateLimitProperties().apply {
            enabled = true
            exportReadMaxAttempts = 10
            exportMaxAttempts = 0
        }
        val filter = LoginRateLimitFilter(LoginRateLimiter(props), ObjectMapper())

        val readRequest = MockHttpServletRequest("GET", "/admin/exports/payments").apply {
            addHeader("Accept", "text/html")
            remoteAddr = "127.0.0.2"
        }
        val readResponse = MockHttpServletResponse()
        filter.doFilter(readRequest, readResponse, MockFilterChain(terminalServlet))

        val heavyRequest = MockHttpServletRequest("POST", "/admin/exports/payments/jobs").apply {
            addHeader("Accept", "application/json")
            remoteAddr = "127.0.0.3"
        }
        val heavyResponse = MockHttpServletResponse()
        filter.doFilter(heavyRequest, heavyResponse, MockFilterChain(terminalServlet))

        assertThat(readResponse.status).isEqualTo(HttpServletResponse.SC_OK)
        assertThat(heavyResponse.status).isEqualTo(429)
        assertThat(heavyResponse.contentAsString).contains("EXPORT")
    }
}
