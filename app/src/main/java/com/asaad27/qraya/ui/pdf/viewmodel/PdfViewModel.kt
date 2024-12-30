package com.asaad27.qraya.ui.pdf.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asaad27.qraya.data.repository.IPdfReaderRepository
import com.asaad27.qraya.ui.pdf.model.PdfDocumentState
import com.asaad27.qraya.ui.pdf.model.PdfLoadingState
import com.asaad27.qraya.ui.pdf.model.PdfPageState
import com.asaad27.qraya.ui.pdf.model.PdfUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfViewModel(
    private val pdfReaderRepository: IPdfReaderRepository,
) : ViewModel() {

    private val _pdfState = MutableStateFlow(PdfUiState())
    val pdfState = _pdfState.asStateFlow()

    private var currentUri: Uri? = null

    fun loadPdf(uri: Uri) {
        if (uri == currentUri) return
        currentUri = uri

        viewModelScope.launch {
            updateLoadingState(PdfLoadingState.Loading)
            try {
                pdfReaderRepository.loadPdf(uri).fold(
                    onSuccess = { pdfInfo ->
                        updateLoadingState(
                            PdfLoadingState.Success,
                            pdfInfo.pageCount.toUInt()
                        )
                    },
                    onFailure = { handleError(it) }
                )
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap {
        return pdfReaderRepository.renderPage(pageIndex, width, height)
            .onSuccess { updatePageState(PdfLoadingState.Success, pageIndex.toUInt()) }
            .getOrThrow()
        }

    suspend fun renderPagesAsync(pages: List<Int>, width: Int, height: Int): List<Bitmap> {
        return pdfReaderRepository.renderPages(pages, width, height)
            .onSuccess { updateLoadingState(PdfLoadingState.Success) }
            .getOrThrow()
    }

    private fun updateLoadingState(state: PdfLoadingState, pageCount: UInt = _pdfState.value.pdfDocumentState.pageCount) {
        _pdfState.update {
            it.copy(
                pdfDocumentState = it.pdfDocumentState.copy(
                    loadingState = state,
                    pageCount = pageCount
                )
            )
        }
    }

    private fun updatePageState(state: PdfLoadingState, pageNumber: UInt) {
        _pdfState.update {
            it.copy(
                currentPageState = PdfPageState(state, pageNumber)
            )
        }
    }

    private fun handleError(exception: Throwable) {
        val errorMessage = "Failed to load PDF: ${exception.message}"
        _pdfState.update {
            it.copy(
                pdfDocumentState = PdfDocumentState(
                    loadingState = PdfLoadingState.Error(errorMessage, exception),
                    pageCount = 0u
                ),
                currentPageState = PdfPageState(
                    loadingState = PdfLoadingState.Error(errorMessage, exception),
                    pageNumber = 0u
                )
            )
        }
    }

    public override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            pdfReaderRepository.cleanup()
        }
    }
}