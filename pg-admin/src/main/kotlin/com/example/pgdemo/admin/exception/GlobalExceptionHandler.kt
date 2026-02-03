package com.example.pgdemo.admin.exception

import com.example.pgdemo.admin.tenant.TenantAccessDeniedException
import com.example.pgdemo.admin.tenant.TenantNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * 전역 예외 처리기
 * Global exception handler
 *
 * NOTE: 테넌트 관련 예외 및 기타 예외를 일관된 형식으로 처리합니다.
 *       Handles tenant-related exceptions and other exceptions in a consistent format.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 테넌트 접근 거부 예외 처리
     * Handle tenant access denied exception
     */
    @ExceptionHandler(TenantAccessDeniedException::class)
    fun handleTenantAccessDenied(ex: TenantAccessDeniedException): ResponseEntity<ErrorResponse> {
        log.warn("Tenant access denied: {}", ex.message)
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                error = "TENANT_ACCESS_DENIED",
                message = ex.message ?: "Access denied",
                timestamp = Instant.now()
            ))
    }

    /**
     * 테넌트 컨텍스트 없음 예외 처리
     * Handle tenant not found exception
     */
    @ExceptionHandler(TenantNotFoundException::class)
    fun handleTenantNotFound(ex: TenantNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Tenant context not found: {}", ex.message)
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(
                error = "TENANT_CONTEXT_NOT_FOUND",
                message = ex.message ?: "Tenant context is required",
                timestamp = Instant.now()
            ))
    }

    /**
     * 리소스 없음 예외 처리
     * Handle resource not found exception
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Resource not found: {}", ex.message)
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                error = "RESOURCE_NOT_FOUND",
                message = ex.message ?: "Resource not found",
                timestamp = Instant.now()
            ))
    }

    /**
     * 잘못된 요청 예외 처리
     * Handle bad request exception
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: {}", ex.message)
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "BAD_REQUEST",
                message = ex.message ?: "Invalid request",
                timestamp = Instant.now()
            ))
    }

    /**
     * 일반 예외 처리
     * Handle general exceptions
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error occurred", ex)
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred",
                timestamp = Instant.now()
            ))
    }
}

/**
 * 에러 응답 DTO
 * Error response DTO
 */
data class ErrorResponse(
    /**
     * 에러 코드
     * Error code
     */
    val error: String,
    
    /**
     * 에러 메시지
     * Error message
     */
    val message: String,
    
    /**
     * 발생 시간
     * Timestamp
     */
    val timestamp: Instant
)

/**
 * 리소스 없음 예외
 * Resource not found exception
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)
