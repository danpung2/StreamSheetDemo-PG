package com.example.pgdemo.admin.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ThymeleafConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/css/**")
            .addResourceLocations("classpath:/static/css/")

        registry
            .addResourceHandler("/js/**")
            .addResourceLocations("classpath:/static/js/")
    }
}
