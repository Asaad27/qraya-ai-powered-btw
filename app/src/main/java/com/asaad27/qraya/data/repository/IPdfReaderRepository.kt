package com.asaad27.qraya.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.asaad27.qraya.data.model.PdfInfo
import kotlinx.coroutines.flow.Flow

interface IPdfReaderRepository {
    suspend fun loadPdf(uri: Uri): Result<PdfInfo>
    suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Result<Bitmap>
    suspend fun renderPages(pages: List<Int>, width: Int, height: Int): Result<List<Bitmap>>
    fun renderPagesFlow(pages: List<Int>, width: Int, height: Int): Flow<Result<Pair<Int, Bitmap>>>
    suspend fun cleanup()
}

