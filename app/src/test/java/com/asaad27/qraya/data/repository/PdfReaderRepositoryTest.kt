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
import org.assertj.core.api.Assertions.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test


class PdfReaderRepositoryTest {
    private lateinit var repository: PdfReaderRepository
    private lateinit var context: Context
    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockkStatic(Bitmap::class)
        mockkStatic(Bitmap::class, ParcelFileDescriptor::class)
        every { ParcelFileDescriptor.dup(any()) } returns mockk(relaxed = true)
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
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor

        // When
        val result = repository.loadPdf(uri)

        // Then
        assertThat(result.isSuccess)
            .withFailMessage("Result should be successful, found: ${result.exceptionOrNull()?.message}")
            .isTrue
        assertThat(result.getOrNull()?.pageCount)
            .isEqualTo(2)
    }

    @Test
    fun `loadPdf returns failure when file descriptor is null`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns null

        // When
        val result = repository.loadPdf(uri)

        assertTrue(result.isFailure)
        assertEquals("Failed to open PDF file descriptor", result.exceptionOrNull()?.message)
        // Then
        assertThat(result.isFailure)
            .withFailMessage("Result should be successful, found: ${result.exceptionOrNull()?.message}")
            .isTrue
    }

    @Test
    fun `renderPage returns bitmap with correct dimensions`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)

        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        // When
        repository.loadPdf(uri).getOrThrow()
        val result = repository.renderPage(0, 300, 400)

        // Then
        assertThat(result.isSuccess)
            .withFailMessage("Result should be successful, found: ${result.exceptionOrNull()?.message}")
            .isTrue
        assertThat(result.getOrNull())
            .withFailMessage("Result should contain a bitmap")
            .isEqualTo(mockBitmap)
    }

    @Test
    fun `renderPages returns list of bitmaps`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        // When
        repository.loadPdf(uri).getOrThrow()
        val result = repository.renderPages(listOf(0, 1), 300, 400)

        // Then
        assertThat(result.isSuccess)
            .withFailMessage("Result should be successful, found: ${result.exceptionOrNull()?.message}")
            .isTrue
        assertThat(result.getOrNull()?.size)
            .isEqualTo(2)
    }

    @Test
    fun `renderPagesFlow emits bitmaps sequentially`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        // When
        repository.loadPdf(uri).getOrThrow()
        val results = mutableListOf<Result<Pair<Int, Bitmap>>>()
        repository.renderPagesFlow(listOf(0, 1), 300, 400).collect { results.add(it) }

        // Then
        assertThat(results.size)
            .isEqualTo(2)
        assertThat(results.all { it.isSuccess })
            .withFailMessage("All results should be successful")
            .isTrue
        assertThat(results[0].getOrNull()?.first)
            .isEqualTo(0)
        assertThat(results[1].getOrNull()?.first)
            .isEqualTo(1)
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.cleanup()
        }
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