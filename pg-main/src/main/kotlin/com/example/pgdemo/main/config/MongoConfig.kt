package com.example.pgdemo.main.config

import com.example.pgdemo.common.domain.document.PaymentExportView
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

@Configuration
class MongoConfig(private val mappingContext: MongoMappingContext) {
    init {
        mappingContext.setInitialEntitySet(setOf(PaymentExportView::class.java))
    }
}
