package com.asaad27.qraya.ui.pdf.model


data class PdfPageState(
    val loadingState: PdfLoadingState,
    val pageNumber: UInt
)