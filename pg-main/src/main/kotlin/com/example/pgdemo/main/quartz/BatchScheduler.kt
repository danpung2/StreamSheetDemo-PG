package com.example.pgdemo.main.quartz

import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.example.pgdemo.main.batch.PaymentExportViewSyncStateStore

@Configuration
class BatchScheduler {
    @Bean
    fun paymentViewSyncJobDetail(): JobDetail {
        return JobBuilder.newJob(PaymentViewSyncQuartzJob::class.java)
            .withIdentity("paymentViewSyncJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun paymentViewSyncTrigger(paymentViewSyncJobDetail: JobDetail): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(paymentViewSyncJobDetail)
            .withIdentity("paymentViewSyncTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMinutes(5)
                    .repeatForever()
            )
            .startNow()
            .build()
    }
}

class PaymentViewSyncQuartzJob : Job {
    @Autowired
    private lateinit var jobLauncher: JobLauncher

    @Autowired
    @Qualifier("paymentViewSyncJob")
    private lateinit var paymentViewSyncJob: org.springframework.batch.core.Job

    @Autowired
    private lateinit var syncStateStore: PaymentExportViewSyncStateStore

    override fun execute(context: JobExecutionContext) {
        syncStateStore.migrateIfNeeded()

        val (paymentFrom, paymentFromId) = syncStateStore.getPaymentLastSyncCursor()
        val (refundFrom, refundFromId) = syncStateStore.getRefundLastSyncCursor()

        val params = JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .addLong("paymentSyncFrom", paymentFrom.toEpochMilli())
            .addString("paymentSyncFromId", paymentFromId?.toString() ?: "")
            .addLong("refundSyncFrom", refundFrom.toEpochMilli())
            .addString("refundSyncFromId", refundFromId?.toString() ?: "")
            .toJobParameters()
        jobLauncher.run(paymentViewSyncJob, params)
    }
}
