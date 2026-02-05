package com.example.pgdemo.main.batch

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "pgdemo.payment-view-sync",
    name = ["startup-enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PaymentExportViewSyncInitializer(
    private val syncRunner: PaymentExportViewSyncRunner
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        syncRunner.runOnStartup()
    }
}
