package com.example.pgdemo.admin.export

import com.streamsheet.core.datasource.StreamingDataSource

class MappedStreamingDataSource<S, T>(
    private val source: StreamingDataSource<S>,
    private val name: String = source.sourceName,
    private val mapper: (S) -> kotlin.sequences.Sequence<T>
) : StreamingDataSource<T> {

    override val sourceName: String
        get() = name

    override fun stream(): kotlin.sequences.Sequence<T> {
        return source.stream().flatMap(mapper)
    }

    override fun stream(filter: Map<String, Any>): kotlin.sequences.Sequence<T> {
        return source.stream(filter).flatMap(mapper)
    }

    override fun close() {
        source.close()
    }
}
