package com.asaad27.qraya.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.asaad27.qraya.data.model.PdfInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfReaderRepository(
    private val applicationContext: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private var rendererFactory: (ParcelFileDescriptor) -> IPdfRenderer = { AndroidPdfRenderer(PdfRenderer(it)) },
    private val matrixFactory: () -> Matrix = { Matrix() }
) : IPdfReaderRepository {

    private var fileDesc: ParcelFileDescriptor? = null
    private var renderer: IPdfRenderer? = null

    override suspend fun loadPdf(uri: Uri): Result<PdfInfo> = withContext(dispatcher) {
        runCatching {
            cleanup()

            fileDesc = applicationContext.contentResolver.openFileDescriptor(uri, "r")?.also {
                renderer = rendererFactory(it)
            } ?: throw Exception("Failed to open PDF file descriptor")

            PdfInfo(pageCount = renderer?.pageCount ?: 0)
        }
    }

    override suspend fun renderPage(
        pageIndex: Int,
        width: Int,
        height: Int
    ): Result<Bitmap> {
        return withContext(dispatcher) {
            runCatching {
                renderer?.openPage(pageIndex)?.use { page ->
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val matrix = matrixFactory().apply {
                        setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    return@use bitmap
                } ?: throw Exception("error during page rendering, null result")
            }
        }
    }

    override suspend fun renderPages(
        pages: List<Int>,
        width: Int,
        height: Int
    ): Result<List<Bitmap>> = withContext(dispatcher) {
        runCatching {
            coroutineScope {
                pages.map { pageIndex ->
                    async {
                        renderPage(pageIndex, width, height).getOrThrow()
                    }
                }.awaitAll()
            }
        }
    }

    override fun renderPagesFlow(
        pages: List<Int>,
        width: Int,
        height: Int
    ): Flow<Result<Pair<Int, Bitmap>>> = channelFlow {
        pages.forEach { pageIndex ->
            launch {
                val result = renderPage(pageIndex, width, height)
                send(result.map { pageIndex to it })
            }
        }
    }.flowOn(dispatcher)

    override fun cleanup() {
        fileDesc?.close()
        renderer?.close()
        fileDesc = null
        renderer = null
    }
}

