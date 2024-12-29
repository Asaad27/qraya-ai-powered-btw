package com.asaad27.qraya.ui.pdf.model

data class PdfUiState(
    val pdfDocumentState: PdfDocumentState = PdfDocumentState(PdfLoadingState.Initial, 0u),
    val currentPageState: PdfPageState = PdfPageState(PdfLoadingState.Initial, 0u)
)