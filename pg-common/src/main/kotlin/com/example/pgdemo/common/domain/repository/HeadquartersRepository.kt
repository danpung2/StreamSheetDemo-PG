package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.entity.Headquarters
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Repository interface for Headquarters entity.
 * 본사 엔티티에 대한 Repository 인터페이스.
 * 
 * Provides data access operations for headquarters management.
 * 본사 관리를 위한 데이터 접근 연산을 제공합니다.
 */
@Repository
interface HeadquartersRepository : JpaRepository<Headquarters, UUID> {
    
    /**
     * Find headquarters by unique code.
     * 고유 코드로 본사를 조회합니다.
     * 
     * @param headquartersCode the unique headquarters code / 고유한 본사 코드
     * @return the headquarters if found, null otherwise / 본사가 존재하면 반환, 없으면 null
     */
    fun findByHeadquartersCode(headquartersCode: String): Headquarters?
    
    /**
     * Check if headquarters exists by code.
     * 코드로 본사 존재 여부를 확인합니다.
     * 
     * @param headquartersCode the headquarters code to check / 확인할 본사 코드
     * @return true if exists, false otherwise / 존재하면 true, 아니면 false
     */
    fun existsByHeadquartersCode(headquartersCode: String): Boolean
    
    /**
     * Find all headquarters with given status.
     * 특정 상태의 모든 본사를 조회합니다.
     * 
     * @param status the status to filter by / 필터링할 상태
     * @return list of headquarters with the given status / 해당 상태의 본사 목록
     */
    fun findByStatus(status: String): List<Headquarters>
    
    /**
     * Find all headquarters with pagination.
     * 페이지네이션으로 모든 본사를 조회합니다.
     * 
     * @param status the status to filter by / 필터링할 상태
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated headquarters result / 페이지네이션된 본사 결과
     */
    fun findByStatus(status: String, pageable: Pageable): Page<Headquarters>
    
    /**
     * Find headquarters by business number.
     * 사업자등록번호로 본사를 조회합니다.
     * 
     * @param businessNumber the business registration number / 사업자등록번호
     * @return the headquarters if found, null otherwise / 본사가 존재하면 반환, 없으면 null
     */
    fun findByBusinessNumber(businessNumber: String): Headquarters?
    
    /**
     * Search headquarters by name containing keyword (case-insensitive).
     * 키워드를 포함하는 이름으로 본사를 검색합니다 (대소문자 무시).
     * 
     * @param name the keyword to search in name / 이름에서 검색할 키워드
     * @param pageable pagination information / 페이지네이션 정보
     * @return paginated search result / 페이지네이션된 검색 결과
     */
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Headquarters>

    fun findByName(name: String): List<Headquarters>
    
    /**
     * Count headquarters by status.
     * 상태별 본사 수를 조회합니다.
     * 
     * @param status the status to count / 카운트할 상태
     * @return the count of headquarters with the given status / 해당 상태의 본사 수
     */
    fun countByStatus(status: String): Long
    
    /**
     * Find all active headquarters IDs.
     * 모든 활성 본사의 ID 목록을 조회합니다.
     * 
     * @return list of active headquarters IDs / 활성 본사 ID 목록
     */
    @Query("SELECT h.id FROM Headquarters h WHERE h.status = 'ACTIVE'")
    fun findAllActiveIds(): List<UUID>
}
