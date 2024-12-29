package com.asaad27.qraya.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test


class PdfReaderRepositoryTest {
    private lateinit var repository: PdfReaderRepository
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkStatic(Bitmap::class)
        val mockMatrix = mockk<Matrix>(relaxed = true)

        repository = PdfReaderRepository(
            applicationContext = context,
            dispatcher = testDispatcher,
            rendererFactory = { TestPdfRenderer(pageCount = 2) },
            matrixFactory = { mockMatrix }
        )
    }

    @Test
    fun `loadPdf returns correct page count`() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor

        val result = repository.loadPdf(uri)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.pageCount)
    }

    @Test
    fun `loadPdf returns failure when file descriptor is null`() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns null

        val result = repository.loadPdf(uri)

        assertTrue(result.isFailure)
        assertEquals("Failed to open PDF file descriptor", result.exceptionOrNull()?.message)
    }

    @Test
    fun `renderPage returns bitmap with correct dimensions`() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)

        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        repository.loadPdf(uri).getOrThrow()
        val result = repository.renderPage(0, 300, 400)

        assertTrue(result.isSuccess)
        assertEquals(mockBitmap, result.getOrNull())
    }

    @Test
    fun `renderPages returns list of bitmaps`() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        repository.loadPdf(uri).getOrThrow()
        val result = repository.renderPages(listOf(0, 1), 300, 400)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `renderPagesFlow emits bitmaps sequentially`() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        repository.loadPdf(uri).getOrThrow()
        val results = mutableListOf<Result<Pair<Int, Bitmap>>>()
        repository.renderPagesFlow(listOf(0, 1), 300, 400).collect { results.add(it) }

        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals(0, results[0].getOrNull()?.first)
        assertEquals(1, results[1].getOrNull()?.first)
    }

    @After
    fun tearDown() {
        repository.cleanup()
        unmockkAll()
    }

    private class TestPdfRenderer(override val pageCount: Int = 1) : IPdfRenderer {
        override fun openPage(index: Int): IPdfPage = TestPdfPage()
        override fun close() {}
    }

    private class TestPdfPage : IPdfPage {
        override val width: Int = 100
        override val height: Int = 100

        override fun render(
            bitmap: Bitmap,
            dest: Rect?,
            transform: Matrix?,
            renderMode: Int
        ) {
            // No-op for test
        }

        override fun close() {}
    }
}