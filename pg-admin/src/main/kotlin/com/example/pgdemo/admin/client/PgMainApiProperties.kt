package com.example.pgdemo.admin.client

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "pg-main")
data class PgMainApiProperties(
    var baseUrl: String = "http://localhost:8080"
)
