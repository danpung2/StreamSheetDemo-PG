package com.example.pgdemo.common.domain.document

import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.ExportJobType
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.enum.UserRole
import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("export_job")
data class ExportJob(
    @Id
    val jobId: String,
    val jobType: ExportJobType,
    val tenantType: TenantType,
    val tenantId: UUID?,
    val userId: UUID,
    val userRole: UserRole,
    val headquartersId: UUID?,
    val merchantId: UUID?,
    val fromUtc: Instant,
    val toUtc: Instant,
    val transactionType: String? = null,
    val transactionStatus: String? = null,
    val status: ExportJobStatus,
    val progressLabel: String?,
    val queuedAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val requestedBy: String,
    val errorSummary: String?,
    val outputLocation: String?
)
