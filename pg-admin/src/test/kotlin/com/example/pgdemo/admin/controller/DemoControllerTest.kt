package com.example.pgdemo.admin.controller

import com.example.pgdemo.admin.security.DemoProperties
import com.example.pgdemo.admin.service.DemoSessionService
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.RedirectView

class DemoControllerTest {
    private val demoProperties = DemoProperties().apply {
        enabled = true
        cookieSameSite = "Lax"
        cookieSecure = false
    }
    private val demoSessionService = mock(DemoSessionService::class.java)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(DemoController(demoProperties, demoSessionService))
        .setViewResolvers(testViewResolver())
        .build()

    @Test
    fun `GET demo page returns 200`() {
        mockMvc.perform(get("/demo"))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST demo start sets cookie and redirects`() {
        given(demoSessionService.issueDemoAccessToken()).willReturn(
            DemoSessionService.DemoAccessToken(
                accessToken = "test-access-token",
                expiresInSeconds = Duration.ofMinutes(15).seconds
            )
        )

        val result = mockMvc.perform(post("/api/demo/start"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/dashboard"))
            .andExpect(header().exists("Set-Cookie"))
            .andReturn()

        val response: MockHttpServletResponse = result.response
        val setCookie = response.getHeader("Set-Cookie")
        requireNotNull(setCookie)

        assertThat(setCookie).contains("accessToken=test-access-token")
        assertThat(setCookie).contains("HttpOnly")
        assertThat(setCookie).contains("SameSite=Lax")
        assertThat(setCookie).contains("Path=/")
    }

    private fun testViewResolver(): ViewResolver {
        return ViewResolver { viewName, _ ->
            if (viewName.startsWith("redirect:")) {
                RedirectView(viewName.removePrefix("redirect:"))
            } else {
                View { _, _, response ->
                    response.status = 200
                }
            }
        }
    }
}
