package com.example.pgdemo.main.batch

import javax.sql.DataSource
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Service

@Service
class PostgresAdvisoryLockService(
    private val dataSource: DataSource
) {
    fun <T> withTryLock(lockKey: Long, action: () -> T): T? {
        val conn = DataSourceUtils.getConnection(dataSource)
        var locked = false
        try {
            conn.prepareStatement("select pg_try_advisory_lock(?)").use { ps ->
                ps.setLong(1, lockKey)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        locked = rs.getBoolean(1)
                    }
                }
            }

            if (!locked) {
                return null
            }

            return try {
                action()
            } finally {
                try {
                    conn.prepareStatement("select pg_advisory_unlock(?)").use { ps ->
                        ps.setLong(1, lockKey)
                        ps.executeQuery().use { /* ignore */ }
                    }
                } catch (ex: Exception) {
                    runCatching { conn.close() }
                    throw ex
                }
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource)
        }
    }
}
