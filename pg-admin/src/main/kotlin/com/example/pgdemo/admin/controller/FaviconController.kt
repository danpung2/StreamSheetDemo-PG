package com.example.pgdemo.admin.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * favicon.ico 요청이 들어올 때 기본 에러 페이지로 떨어지는 것을 방지합니다.
 * Prevents favicon.ico requests from rendering the default error page.
 */
@RestController
class FaviconController {
    @GetMapping("/favicon.ico")
    fun favicon(): ResponseEntity<Void> {
        return ResponseEntity.noContent().build()
    }
}
