package com.asaad27.qraya.ui.pdf.model

import com.asaad27.qraya.ui.pdf.model.PdfDocumentState

data class PdfUiState(
    val pdfDocumentState: PdfDocumentState = PdfDocumentState(PdfLoadingState.Initial, 0u),
    val currentPageState: PdfPageState = PdfPageState(PdfLoadingState.Initial, 0u)
)