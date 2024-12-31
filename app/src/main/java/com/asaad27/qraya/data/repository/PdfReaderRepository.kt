package com.asaad27.qraya.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.asaad27.qraya.data.model.PdfInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PdfReaderRepository(
    private val applicationContext: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private var rendererFactory: (ParcelFileDescriptor) -> IPdfRenderer = {
        AndroidPdfRenderer(
            PdfRenderer(it)
        )
    },
    private val matrixFactory: () -> Matrix = { Matrix() }
) : IPdfReaderRepository {

    private val rendererPoolSize: Int = Runtime.getRuntime().availableProcessors()
    private val rendererPool = Channel<IPdfRenderer>(Channel.UNLIMITED)
    private val activeRenderers = ConcurrentLinkedQueue<IPdfRenderer>()
    private var currentUri: Uri? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    override suspend fun loadPdf(uri: Uri): Result<PdfInfo> = withContext(dispatcher) {
        runCatching {
            cleanup()

            currentUri = uri

            val fd = applicationContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw Exception("Failed to open PDF file descriptor")
            fileDescriptor = fd

            // Initialize a temporary renderer to get page count
            val tempRenderer = rendererFactory(ParcelFileDescriptor.dup(fd.fileDescriptor))
            val pageCount = tempRenderer.pageCount
            tempRenderer.close()

            // Initialize renderer pool
            repeat(rendererPoolSize) {
                val clonedFd = ParcelFileDescriptor.dup(fd.fileDescriptor)
                val renderer = rendererFactory(clonedFd)
                activeRenderers.add(renderer)
                rendererPool.trySend(renderer)
            }

            PdfInfo(pageCount = pageCount)
        }
    }


    override suspend fun renderPage(
        pageIndex: Int,
        width: Int,
        height: Int
    ): Result<Bitmap> = withContext(dispatcher) {
        runCatching {
            withRenderer { renderer ->
                renderPageInternal(pageIndex, width, height, renderer).getOrThrow()
            }
        }
    }

    override suspend fun renderPages(
        pages: List<Int>,
        width: Int,
        height: Int
    ): Result<List<Bitmap>> = withContext(dispatcher) {
        runCatching {
            val batchSize = (pages.size + rendererPoolSize - 1) / rendererPoolSize
            val batches = pages.chunked(batchSize)
            val startTime = System.nanoTime()

            Log.d("PdfReaderRepository", "started Rendering ${pages.size} pages in $batchSize batches")

            coroutineScope {
                batches.map { batch ->
                    async {
                        withRenderer { renderer ->
                            batch.map { pageIndex ->
                                renderPageInternal(pageIndex, width, height, renderer).getOrThrow()
                            }
                        }
                    }
                }.awaitAll().flatten().also {
                    Log.d("PdfReaderRepository", "finished Rendering ${pages.size} pages in $batchSize batches")

                    val totalDurationNs = System.nanoTime() - startTime
                    val totalDurationMs = totalDurationNs / 1_000_000
                    val seconds = totalDurationMs / 1000
                    val milliseconds = totalDurationMs % 1000
                    Log.d("PdfReaderRepository", "took $seconds seconds and $milliseconds milliseconds to render $pages pages")
                }
            }
        }
    }

    override fun renderPagesFlow(
        pages: List<Int>,
        width: Int,
        height: Int
    ): Flow<Result<Pair<Int, Bitmap>>> = channelFlow {
        val batchSize = (pages.size + rendererPoolSize - 1) / rendererPoolSize
        val batches = pages.chunked(batchSize)

        coroutineScope {
            batches.forEach { batch ->
                launch {
                    withRenderer { renderer ->
                        batch.forEach { pageIndex ->
                            val result = renderPageInternal(pageIndex, width, height, renderer)
                            send(result.map { pageIndex to it })
                        }
                    }
                }
            }
        }
    }.flowOn(dispatcher)


    private fun renderPageInternal(
        pageIndex: Int,
        width: Int,
        height: Int,
        renderer: IPdfRenderer
    ): Result<Bitmap> = runCatching {
        Log.d("PdfReaderRepository", "Rendering page $pageIndex")
        renderer.openPage(pageIndex).use { page ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val matrix = matrixFactory().apply {
                setScale(width.toFloat() / page.width, height.toFloat() / page.height)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }.also {
        Log.d("PdfReaderRepository", "Page $pageIndex rendering completed")
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun cleanup() = withContext(dispatcher) {
        activeRenderers.forEach { renderer ->
            runCatching { renderer.close() }
                .onFailure { Log.e("PdfReaderRepository", "Error closing renderer", it) }
        }
        activeRenderers.clear()

        runCatching {
            fileDescriptor?.close()
            fileDescriptor = null
        }.onFailure { Log.e("PdfReaderRepository", "Error closing file descriptor", it) }

        currentUri = null

        while (true) {
            val renderer = rendererPool.tryReceive().getOrNull() ?: break
            runCatching { renderer.close() }
                .onFailure { Log.e("PdfReaderRepository", "Error closing pooled renderer", it) }
        }
    }

    internal fun getActiveRenderersCount() = activeRenderers.size
    internal fun getPooledRenderersCount(): Int {
        var count = 0
        val tempList = mutableListOf<IPdfRenderer>()

        while (true) {
            val renderer = rendererPool.tryReceive().getOrNull() ?: break
            tempList.add(renderer)
            count++
        }

        tempList.forEach { renderer ->
            rendererPool.trySend(renderer)
        }

        return count
    }

    private suspend fun <T> withRenderer(timeout: Duration = 10.seconds , block: suspend (IPdfRenderer) -> T): T {
        val renderer = withTimeout(timeout) {
            rendererPool.receive()
        }
        try {
            return block(renderer)
        } finally {
            withTimeout(timeout) {
                rendererPool.send(renderer)
            }
        }
    }
}

