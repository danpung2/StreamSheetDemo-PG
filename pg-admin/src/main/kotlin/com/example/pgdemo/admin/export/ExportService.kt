package com.example.pgdemo.admin.export

import com.example.pgdemo.admin.tenant.TenantAccessDeniedException
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.admin.tenant.TenantPermissionMatrix
import com.example.pgdemo.common.domain.`enum`.TenantType
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.mongodb.MongoStreamingDataSource
import com.streamsheet.core.schema.AnnotationExcelSchema
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class ExportService(
    private val excelExporter: ExcelExporter,
    private val mongoTemplate: MongoTemplate,
    private val tenantPermissionMatrix: TenantPermissionMatrix
) {
    fun exportPayments(
        startDate: LocalDate,
        endDate: LocalDate,
        outputStream: OutputStream,
        headquartersId: UUID?,
        merchantId: UUID?
    ) {
        val tenantInfo = TenantContext.require()
        tenantPermissionMatrix.requireExport(tenantInfo, TenantPermissionMatrix.Resource.PAYMENT)

        validateRequestedFilters(tenantInfo.tenantType, tenantInfo.tenantId, headquartersId, merchantId)

        val criteria = Criteria.where("paymentDate")
            .gte(startDate.atStartOfDay(ZoneOffset.UTC).toInstant())
            .lt(endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())

        when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> {
                headquartersId?.let { criteria.and("headquartersId").`is`(it) }
                merchantId?.let { criteria.and("merchantId").`is`(it) }
            }
            TenantType.HEADQUARTERS -> {
                val tenantHeadquartersId = tenantInfo.tenantId
                    ?: throw TenantAccessDeniedException("Headquarters tenant requires tenantId")
                criteria.and("headquartersId").`is`(tenantHeadquartersId)
                merchantId?.let { criteria.and("merchantId").`is`(it) }
            }
            TenantType.MERCHANT -> {
                val tenantMerchantId = tenantInfo.tenantId
                    ?: throw TenantAccessDeniedException("Merchant tenant requires tenantId")
                criteria.and("merchantId").`is`(tenantMerchantId)
            }
        }

        val schema = AnnotationExcelSchema.create<PaymentExportDto>()
        val dataSource = MongoStreamingDataSource.create<PaymentExportDto>(
            mongoTemplate = mongoTemplate,
            query = Query.query(criteria)
        )

        excelExporter.export(schema, dataSource, outputStream)
    }

    private fun validateRequestedFilters(
        tenantType: TenantType,
        tenantId: UUID?,
        headquartersId: UUID?,
        merchantId: UUID?
    ) {
        when (tenantType) {
            TenantType.OPERATOR -> Unit
            TenantType.HEADQUARTERS -> {
                if (headquartersId != null && headquartersId != tenantId) {
                    throw TenantAccessDeniedException("Headquarters cannot filter other headquarters")
                }
            }
            TenantType.MERCHANT -> {
                if (headquartersId != null) {
                    throw TenantAccessDeniedException("Merchant cannot filter by headquarters")
                }
                if (merchantId != null && merchantId != tenantId) {
                    throw TenantAccessDeniedException("Merchant cannot filter other merchants")
                }
            }
        }
    }
}
