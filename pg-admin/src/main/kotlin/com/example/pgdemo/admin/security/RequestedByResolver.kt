package com.example.pgdemo.admin.security

import com.example.pgdemo.common.domain.entity.AdminUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

object RequestedByResolver {
    fun currentLabel(): String {
        val auth = SecurityContextHolder.getContext().authentication ?: return "-"
        val principal = auth.principal

        return when (principal) {
            is AdminUser -> principal.email
            is UserDetails -> principal.username
            else -> auth.name.takeIf { it.isNotBlank() } ?: "-"
        }
    }
}
