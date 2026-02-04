package com.example.pgdemo.admin.export

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet

@ExcelSheet(name = "거래 내역")
data class TransactionExportDto(
    @ExcelColumn(header = "거래번호", width = 40, order = 1)
    val transactionId: String,

    @ExcelColumn(header = "거래유형", width = 10, order = 2)
    val transactionType: String,

    @ExcelColumn(header = "본사코드", width = 15, order = 3)
    val headquartersCode: String,

    @ExcelColumn(header = "본사명", width = 25, order = 4)
    val headquartersName: String,

    @ExcelColumn(header = "업체코드", width = 20, order = 5)
    val merchantCode: String,

    @ExcelColumn(header = "업체명", width = 30, order = 6)
    val merchantName: String,

    @ExcelColumn(header = "주문번호", width = 25, order = 7)
    val orderId: String,

    @ExcelColumn(header = "금액", width = 15, order = 8)
    val amount: Long,

    @ExcelColumn(header = "수단", width = 10, order = 9)
    val method: String,

    @ExcelColumn(header = "상태", width = 18, order = 10)
    val status: String,

    @ExcelColumn(header = "거래일시", width = 20, order = 11)
    val transactionDate: String,

    @ExcelColumn(header = "payment id", width = 40, order = 12)
    val paymentId: String?
)
