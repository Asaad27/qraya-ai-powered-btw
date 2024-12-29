package com.asaad27.qraya.ui.pdf.model

data class PdfDocumentState(
    val loadingState: PdfLoadingState,
    val pageCount: UInt,
)