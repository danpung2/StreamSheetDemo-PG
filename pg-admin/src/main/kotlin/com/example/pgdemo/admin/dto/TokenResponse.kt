package com.example.pgdemo.admin.dto

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)
