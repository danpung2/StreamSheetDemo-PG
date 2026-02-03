package com.example.pgdemo.admin.tenant

/**
 * ThreadLocal 기반 테넌트 컨텍스트 관리자
 * ThreadLocal-based tenant context manager
 *
 * NOTE: 현재 스레드의 테넌트 정보를 저장하고 조회하는 유틸리티입니다.
 *       반드시 요청 처리 완료 후 clear()를 호출해야 메모리 누수를 방지할 수 있습니다.
 *       This utility stores and retrieves tenant information for the current thread.
 *       Always call clear() after request processing to prevent memory leaks.
 */
object TenantContext {
    
    /**
     * 현재 스레드의 테넌트 정보를 저장하는 ThreadLocal
     * ThreadLocal storing tenant information for the current thread
     */
    private val tenantHolder = ThreadLocal<TenantInfo>()

    /**
     * 현재 스레드에 테넌트 정보 설정
     * Set tenant information for the current thread
     *
     * @param tenantInfo 설정할 테넌트 정보 / Tenant information to set
     */
    fun set(tenantInfo: TenantInfo) {
        tenantHolder.set(tenantInfo)
    }

    /**
     * 현재 스레드의 테넌트 정보 조회
     * Get tenant information for the current thread
     *
     * @return 테넌트 정보, 없으면 null / Tenant information or null if not set
     */
    fun get(): TenantInfo? = tenantHolder.get()

    /**
     * 현재 스레드의 테넌트 정보 조회 (필수)
     * Get tenant information for the current thread (required)
     *
     * @return 테넌트 정보 / Tenant information
     * @throws TenantNotFoundException 테넌트 정보가 없을 경우 / If tenant information is not found
     */
    fun require(): TenantInfo = get() ?: throw TenantNotFoundException("Tenant context is not set")

    /**
     * 현재 스레드의 테넌트 정보 제거
     * Clear tenant information for the current thread
     *
     * NOTE: 반드시 요청 처리 완료 후 호출해야 합니다.
     *       Must be called after request processing is complete.
     */
    fun clear() {
        tenantHolder.remove()
    }

    /**
     * 테넌트 컨텍스트가 설정되어 있는지 확인
     * Check if tenant context is set
     *
     * @return 설정 여부 / Whether the context is set
     */
    fun isSet(): Boolean = tenantHolder.get() != null
}

/**
 * 테넌트 정보가 없을 때 발생하는 예외
 * Exception thrown when tenant information is not found
 */
class TenantNotFoundException(message: String) : RuntimeException(message)
