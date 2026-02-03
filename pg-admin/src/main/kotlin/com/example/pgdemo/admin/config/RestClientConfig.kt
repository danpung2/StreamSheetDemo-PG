package com.example.pgdemo.admin.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @Value("\${pg-main.base-url}") private val baseUrl: String
) {

    @Bean
    fun restClient(builder: RestClient.Builder): RestClient {
        return builder
            .baseUrl(baseUrl)
            .build()
    }
}
