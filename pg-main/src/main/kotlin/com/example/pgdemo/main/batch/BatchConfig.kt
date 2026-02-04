package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.entity.RefundTransaction
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class BatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {
    @Bean
    fun paymentViewSyncPaymentsStep(
        @Qualifier("paymentTransactionReader") reader: ItemReader<PaymentTransaction>,
        @Qualifier("paymentExportViewProcessor") processor: ItemProcessor<PaymentTransaction, PaymentExportViewUpsertItem>,
        @Qualifier("paymentExportViewUpsertPaymentWriter") writer: ItemWriter<PaymentExportViewUpsertItem>
    ): Step {
        return StepBuilder("paymentViewSyncPaymentsStep", jobRepository)
            .chunk<PaymentTransaction, PaymentExportViewUpsertItem>(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }

    @Bean
    fun paymentViewSyncRefundsStep(
        @Qualifier("refundTransactionReader") reader: ItemReader<RefundTransaction>,
        @Qualifier("refundExportViewProcessor") processor: ItemProcessor<RefundTransaction, PaymentExportViewUpsertItem>,
        @Qualifier("paymentExportViewUpsertRefundWriter") writer: ItemWriter<PaymentExportViewUpsertItem>
    ): Step {
        return StepBuilder("paymentViewSyncRefundsStep", jobRepository)
            .chunk<RefundTransaction, PaymentExportViewUpsertItem>(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }

    @Bean(name = ["paymentViewSyncJob"])
    fun paymentViewSyncJob(paymentViewSyncPaymentsStep: Step, paymentViewSyncRefundsStep: Step): Job {
        return JobBuilder("paymentViewSyncJob", jobRepository)
            .start(paymentViewSyncPaymentsStep)
            .next(paymentViewSyncRefundsStep)
            .build()
    }
}
