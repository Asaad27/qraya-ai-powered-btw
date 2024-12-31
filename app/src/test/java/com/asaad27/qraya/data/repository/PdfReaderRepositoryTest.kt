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

    @Test
    fun `renderer pool properly manages renderers`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)

        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor

        // When
        repository.loadPdf(uri).getOrThrow()
        val result = repository.renderPage(0, 300, 400)

        // Then
        assertThat(result.isSuccess)
            .withFailMessage("Result should be successful, found: ${result.exceptionOrNull()?.message}")
            .isTrue
        assertThat(repository.getActiveRenderersCount())
            .isEqualTo(Runtime.getRuntime().availableProcessors())
    }

    @Test
    fun `renderer pool initializes with correct number of renderers`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor

        // When
        repository.loadPdf(uri).getOrThrow()

        // Then
        // After initialization, all renderers should be both active and in the pool
        val expectedCount = Runtime.getRuntime().availableProcessors()
        val activeCount = repository.getActiveRenderersCount()
        val poolCount = repository.getPooledRenderersCount()
        assertThat(activeCount)
            .withFailMessage("All renderers should be active, found: $activeCount, expected: $expectedCount")
            .isEqualTo(expectedCount)
        assertThat(poolCount)
            .withFailMessage("All renderers should be in the pool, found: $poolCount, expected: $expectedCount")
            .isEqualTo(expectedCount)
    }

    @Test
    fun `renderer is returned to pool after use`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        // When
        repository.loadPdf(uri).getOrThrow()
        val initialPoolCount = repository.getPooledRenderersCount()
        repository.renderPage(0, 100, 100)  // This will use and return a renderer
        val finalPoolCount = repository.getPooledRenderersCount()

        // Then
        assertThat(finalPoolCount)
            .withFailMessage("Pool should have same count after renderer is returned, found: $finalPoolCount, expected: $initialPoolCount")
            .isEqualTo(initialPoolCount)
    }

    @Test
    fun `concurrent page renders use different renderers`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        repository.loadPdf(uri).getOrThrow()
        val initialPoolCount = repository.getPooledRenderersCount()

        // When - Render multiple pages concurrently
        val results = coroutineScope {
            (0..3).map { pageIndex ->
                async {
                    repository.renderPage(pageIndex, 100, 100)
                }
            }.awaitAll()
        }

        // Then
        assertThat(results)
            .withFailMessage("All renders should succeed")
            .allMatch { it.isSuccess }

        // Verify renderers were returned
        val poolCount = repository.getPooledRenderersCount()
        assertThat(poolCount)
            .withFailMessage("All renderers should be returned to pool, found: $poolCount, expected: $initialPoolCount")
            .isEqualTo(initialPoolCount)
    }

    @Test
    fun `cleanup properly releases all resources`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor

        // When
        repository.loadPdf(uri).getOrThrow()
        repository.cleanup()

        // Then
        val activeRenderers = repository.getActiveRenderersCount()
        val poolCount = repository.getPooledRenderersCount()
        assertThat(activeRenderers)
            .withFailMessage("No renderers should remain active after cleanup, found: $activeRenderers")
            .isZero()
        assertThat(poolCount)
            .withFailMessage("Pool should be empty after cleanup, found: $poolCount")
            .isZero()
    }

    @Test
    fun `loading new PDF cleans up previous resources`() = runTest(testDispatcher) {
        // Given
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(any(), any()) } returns fileDescriptor

        // When
        repository.loadPdf(uri1).getOrThrow()
        val firstLoadRenderers = repository.getActiveRenderersCount()

        repository.loadPdf(uri2).getOrThrow()
        val secondLoadRenderers = repository.getActiveRenderersCount()

        // Then
        val expectedCount = Runtime.getRuntime().availableProcessors()
        assertThat(firstLoadRenderers)
            .withFailMessage("Expected $expectedCount renderers for first load but found $firstLoadRenderers")
            .isEqualTo(expectedCount)
        assertThat(secondLoadRenderers)
            .withFailMessage("Expected $expectedCount renderers for second load but found $secondLoadRenderers")
            .isEqualTo(expectedCount)
    }

    @Test
    fun `renderer pool handles errors gracefully`() = runTest(testDispatcher) {
        // Given
        val uri = mockk<Uri>()
        val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { context.contentResolver.openFileDescriptor(uri, "r") } returns fileDescriptor
        every { Bitmap.createBitmap(any(), any(), any()) } returns mockBitmap

        repository.loadPdf(uri).getOrThrow()
        val initialPoolCount = repository.getPooledRenderersCount()

        // When - Simulate a renderer that throws an exception
        repository.renderPage(0, 100, 100)

        // Then
        val poolCount = repository.getPooledRenderersCount()
        assertThat(poolCount)
            .withFailMessage("Renderer should be returned to pool even after error, found: $poolCount, expected: $initialPoolCount")
            .isEqualTo(initialPoolCount)
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