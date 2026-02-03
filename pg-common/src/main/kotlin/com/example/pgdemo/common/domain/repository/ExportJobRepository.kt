package com.example.pgdemo.common.domain.repository

import com.example.pgdemo.common.domain.document.ExportJob
import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.ExportJobType
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ExportJobRepository : MongoRepository<ExportJob, String> {
    fun findByJobType(jobType: ExportJobType, pageable: Pageable): Page<ExportJob>

    fun findByJobTypeAndStatus(jobType: ExportJobType, status: ExportJobStatus, pageable: Pageable): Page<ExportJob>

    fun findByJobTypeAndTenantTypeAndTenantId(
        jobType: ExportJobType,
        tenantType: com.example.pgdemo.common.domain.enum.TenantType,
        tenantId: UUID?,
        pageable: Pageable
    ): Page<ExportJob>
}
