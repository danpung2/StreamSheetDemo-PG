package com.example.pgdemo.admin.export

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet

@ExcelSheet(name = "결제 내역")
data class PaymentExportDto(
    @ExcelColumn(header = "거래번호", width = 40, order = 1)
    val transactionId: String,

    @ExcelColumn(header = "본사코드", width = 15, order = 2)
    val headquartersCode: String,

    @ExcelColumn(header = "본사명", width = 25, order = 3)
    val headquartersName: String,

    @ExcelColumn(header = "업체코드", width = 20, order = 4)
    val merchantCode: String,

    @ExcelColumn(header = "업체명", width = 30, order = 5)
    val merchantName: String,

    @ExcelColumn(header = "점포유형", width = 10, order = 6)
    val storeType: String,

    @ExcelColumn(header = "업종", width = 15, order = 7)
    val businessType: String,

    @ExcelColumn(header = "주문번호", width = 25, order = 8)
    val orderId: String,

    @ExcelColumn(header = "결제금액", width = 15, order = 9)
    val amount: Long,

    @ExcelColumn(header = "결제수단", width = 10, order = 10)
    val paymentMethod: String,

    @ExcelColumn(header = "결제상태", width = 15, order = 11)
    val paymentStatus: String,

    @ExcelColumn(header = "결제일시", width = 20, order = 12)
    val paymentDate: String,

    @ExcelColumn(header = "환불금액", width = 15, order = 13)
    val refundAmount: Long?,

    @ExcelColumn(header = "환불상태", width = 15, order = 14)
    val refundStatus: String?,

    @ExcelColumn(header = "환불사유", width = 30, order = 15)
    val refundReason: String?
)
