package com.example.pgdemo.main.batch

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class PaymentExportViewSyncInitializer(
    private val syncStateStore: PaymentExportViewSyncStateStore
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        syncStateStore.migrateIfNeeded()
    }
}
