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
                    .withIntervalInHours(1)
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

    override fun execute(context: JobExecutionContext) {
        val params = JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters()
        jobLauncher.run(paymentViewSyncJob, params)
    }
}
