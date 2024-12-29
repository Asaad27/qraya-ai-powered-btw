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
import kotlinx.coroutines.flow.Flow
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
            try {
                _pdfState.update {
                    it.copy(
                        pdfDocumentState = PdfDocumentState(
                            loadingState = PdfLoadingState.Loading,
                            pageCount = 0u
                        ),
                        currentPageState = PdfPageState(PdfLoadingState.Loading, 0u)
                    )
                }

                pdfReaderRepository.loadPdf(uri).fold(
                    onSuccess = { pdfInfo ->
                        _pdfState.update {
                            it.copy(
                                pdfDocumentState = PdfDocumentState(
                                    loadingState = PdfLoadingState.Success,
                                    pageCount = pdfInfo.pageCount.toUInt()
                                ),
                                currentPageState = PdfPageState(PdfLoadingState.Success, 0u)
                            )
                        }
                    },
                    onFailure = { exception ->
                        handleError(exception)
                    }
                )
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap {
        return pdfReaderRepository.renderPage(pageIndex, width, height).getOrThrow()
    }

    suspend fun renderPagesAsync(pages: List<Int>, width: Int, height: Int): List<Bitmap> {
        return pdfReaderRepository.renderPages(pages, width, height).getOrThrow()
    }

    fun renderPagesFlow(
        pages: List<Int>,
        width: Int,
        height: Int
    ): Flow<Result<Pair<Int, Bitmap>>> {
        return pdfReaderRepository.renderPagesFlow(pages, width, height)
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

    override fun onCleared() {
        super.onCleared()
        pdfReaderRepository.cleanup()
    }
}