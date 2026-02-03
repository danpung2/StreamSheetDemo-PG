package com.example.pgdemo.admin.view

import com.example.pgdemo.common.domain.repository.AdminUserRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class UserViewController(
    private val adminUserRepository: AdminUserRepository
) {

    @GetMapping("/users")
    fun users(model: Model): String {
        model.addAttribute("pageTitle", "Users")
        var loadError = false
        val adminUsers = try {
            adminUserRepository.findAll(PageRequest.of(0, 50)).content
        } catch (ex: Exception) {
            loadError = true
            emptyList()
        }
        model.addAttribute(
            "users",
            adminUsers.map { adminUser ->
                mapOf(
                    "name" to adminUser.name,
                    "email" to adminUser.email,
                    "role" to formatLabel(adminUser.role.name),
                    "lastActive" to formatInstant(adminUser.lastLoginAt ?: adminUser.createdAt),
                    "status" to formatLabel(adminUser.status),
                    "statusClass" to statusClass(adminUser.status)
                )
            }
        )
        if (loadError) {
            model.addAttribute("loadError", true)
        }
        return "users/list"
    }

    private fun formatInstant(instant: Instant?): String {
        if (instant == null) {
            return "-"
        }
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    private fun statusClass(status: String): String {
        return if (status.equals("ACTIVE", ignoreCase = true)) {
            "success"
        } else {
            "warning"
        }
    }

    private fun formatLabel(value: String): String {
        return value.lowercase()
            .split("_", " ")
            .joinToString(" ") { segment ->
                segment.replaceFirstChar { char -> char.uppercase() }
            }
    }
}
