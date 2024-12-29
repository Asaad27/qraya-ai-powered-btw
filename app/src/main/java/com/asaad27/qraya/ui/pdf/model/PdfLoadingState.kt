package com.asaad27.qraya.ui.pdf.model

sealed class PdfLoadingState {
    data object Initial : PdfLoadingState()
    data object Loading : PdfLoadingState()
    data object Success : PdfLoadingState()
    data class Error(val message: String, val exception: Throwable) : PdfLoadingState()
}