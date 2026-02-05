package com.example.pgdemo.main.quartz

import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.CronScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.example.pgdemo.main.batch.PaymentExportViewSyncRunner
import java.util.TimeZone

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
                CronScheduleBuilder.cronSchedule("0 0/5 * ? * *")
                    .inTimeZone(TimeZone.getDefault())
                    .withMisfireHandlingInstructionDoNothing()
            )
            .startNow()
            .build()
    }
}

class PaymentViewSyncQuartzJob : Job {
    @Autowired
    private lateinit var syncRunner: PaymentExportViewSyncRunner

    override fun execute(context: JobExecutionContext) {
        syncRunner.runScheduledIfNeeded()
    }
}
