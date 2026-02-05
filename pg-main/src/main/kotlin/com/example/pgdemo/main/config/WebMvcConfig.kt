package com.example.pgdemo.main.config

import com.example.pgdemo.main.security.InternalApiKeyAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val internalApiKeyAuthInterceptor: InternalApiKeyAuthInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(internalApiKeyAuthInterceptor)
            .addPathPatterns("/api/v1/internal/**")
    }
}
