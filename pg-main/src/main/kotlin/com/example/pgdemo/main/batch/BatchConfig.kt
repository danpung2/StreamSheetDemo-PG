package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class BatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {
    @Bean
    fun paymentViewSyncStep(
        reader: ItemReader<PaymentTransaction>,
        processor: ItemProcessor<PaymentTransaction, PaymentExportView>,
        writer: ItemWriter<PaymentExportView>
    ): Step {
        return StepBuilder("paymentViewSyncStep", jobRepository)
            .chunk<PaymentTransaction, PaymentExportView>(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }

    @Bean(name = ["paymentViewSyncJob"])
    fun paymentViewSyncJob(paymentViewSyncStep: Step): Job {
        return JobBuilder("paymentViewSyncJob", jobRepository)
            .start(paymentViewSyncStep)
            .build()
    }
}
