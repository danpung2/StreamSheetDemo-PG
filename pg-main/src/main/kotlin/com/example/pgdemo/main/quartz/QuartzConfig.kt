package com.example.pgdemo.main.quartz

import javax.sql.DataSource
import org.quartz.spi.TriggerFiredBundle
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.scheduling.quartz.SpringBeanJobFactory

@Configuration
class QuartzConfig {
    @Bean
    fun jobFactory(applicationContext: ApplicationContext): SpringBeanJobFactory {
        val jobFactory = AutowiringSpringBeanJobFactory()
        jobFactory.setApplicationContext(applicationContext)
        return jobFactory
    }

    @Bean
    fun schedulerFactoryBean(
        jobFactory: SpringBeanJobFactory,
        dataSource: DataSource
    ): SchedulerFactoryBean {
        val factoryBean = SchedulerFactoryBean()
        factoryBean.setJobFactory(jobFactory)
        factoryBean.setDataSource(dataSource)
        return factoryBean
    }
}

class AutowiringSpringBeanJobFactory : SpringBeanJobFactory(), ApplicationContextAware {
    private lateinit var beanFactory: AutowireCapableBeanFactory

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        beanFactory = applicationContext.autowireCapableBeanFactory
    }

    override fun createJobInstance(bundle: TriggerFiredBundle): Any {
        val job = super.createJobInstance(bundle)
        beanFactory.autowireBean(job)
        return job
    }
}
