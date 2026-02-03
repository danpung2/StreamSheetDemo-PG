package com.example.pgdemo.main.config

import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.repository.PaymentExportViewRepository
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@EnableMongoRepositories(basePackageClasses = [PaymentExportViewRepository::class])
class MongoConfig(private val mappingContext: MongoMappingContext) {
    init {
        mappingContext.setInitialEntitySet(setOf(PaymentExportView::class.java))
    }
}
