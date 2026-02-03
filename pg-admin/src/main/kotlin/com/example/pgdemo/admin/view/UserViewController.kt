package com.example.pgdemo.admin.view

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class UserViewController {

    @GetMapping("/users")
    fun users(model: Model): String {
        model.addAttribute("pageTitle", "Users")
        model.addAttribute(
            "users",
            listOf(
                mapOf(
                    "name" to "Jordan Rivera",
                    "email" to "admin@pgdemo.com",
                    "role" to "Super admin",
                    "lastActive" to "2m ago",
                    "status" to "Active",
                    "statusClass" to "success"
                ),
                mapOf(
                    "name" to "Priya Nair",
                    "email" to "priya@pgdemo.com",
                    "role" to "Ops manager",
                    "lastActive" to "35m ago",
                    "status" to "Active",
                    "statusClass" to "success"
                ),
                mapOf(
                    "name" to "Kenji Tanaka",
                    "email" to "kenji@pgdemo.com",
                    "role" to "Analyst",
                    "lastActive" to "Yesterday",
                    "status" to "Idle",
                    "statusClass" to "warning"
                )
            )
        )
        return "users/list"
    }
}
