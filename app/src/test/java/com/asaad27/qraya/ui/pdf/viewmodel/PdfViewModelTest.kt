package com.asaad27.qraya.ui.pdf.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import com.asaad27.qraya.data.model.PdfInfo
import com.asaad27.qraya.data.repository.IPdfReaderRepository
import com.asaad27.qraya.ui.pdf.model.PdfLoadingState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock

@OptIn(ExperimentalCoroutinesApi::class)
class PdfViewModelTest : KoinTest {

    private val viewModel: PdfViewModel by inject()
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mockProvider = MockProviderRule.create { clazz ->
        mockkClass(clazz)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        startKoin {
            modules(module {
                single<IPdfReaderRepository> {
                    mockkClass(IPdfReaderRepository::class)
                }
                single { PdfViewModel(get()) }
            })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun `loadPdf success should update state correctly`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        val mockPdfInfo = PdfInfo(5)
        declareMock<IPdfReaderRepository> {
            coEvery { loadPdf(mockUri) } returns Result.success(mockPdfInfo)
        }

        // When
        viewModel.loadPdf(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val currentState = viewModel.pdfState.value
        assertEquals(PdfLoadingState.Success, currentState.pdfDocumentState.loadingState)
        assertEquals(5u, currentState.pdfDocumentState.pageCount)
    }

    @Test
    fun `loadPdf error should update state with error`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        val exception = Exception("PDF load failed")
        declareMock<IPdfReaderRepository> {
            coEvery { loadPdf(mockUri) } returns Result.failure(exception)
        }

        // When
        viewModel.loadPdf(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val currentState = viewModel.pdfState.value
        assert(currentState.pdfDocumentState.loadingState is PdfLoadingState.Error)
        assertEquals(0u, currentState.pdfDocumentState.pageCount)
    }

    @Test
    fun `renderPage should return bitmap on success`() = runTest {
        // Given
        val mockBitmap = mockk<Bitmap>()
        declareMock<IPdfReaderRepository> {
            coEvery {
                renderPage(any(), any(), any())
            } returns Result.success(mockBitmap)
        }

        // When
        val result = viewModel.renderPage(pageIndex = 0, width = 100, height = 100)

        // Then
        assertEquals(mockBitmap, result)
    }

    @Test
    fun `renderPages should return list of bitmaps on success`() = runTest {
        // Given
        val mockBitmap = mockk<Bitmap>()
        val pages = listOf(0, 1)
        declareMock<IPdfReaderRepository> {
            coEvery {
                renderPages(pages, any(), any())
            } returns Result.success(listOf(mockBitmap, mockBitmap))
        }

        // When
        val result = viewModel.renderPagesAsync(pages = pages, width = 100, height = 100)

        // Then
        assertEquals(listOf(mockBitmap, mockBitmap), result)
    }

    @Test
    fun `renderPage should throw exception on failure`() = runTest {
        // Given
        val exception = RuntimeException("Render failed")
        declareMock<IPdfReaderRepository> {
            coEvery {
                renderPage(any(), any(), any())
            } returns Result.failure(exception)
        }

        // When & Then
        try {
            viewModel.renderPage(0, 100, 100)
            assert(false) { "Expected exception was not thrown" }
        } catch (e: RuntimeException) {
            assertEquals("Render failed", e.message)
        }
    }

    @Test
    fun `cleanup should be called when viewModel is cleared`() = runTest {
        // Given
        var cleanupCalled = false
        declareMock<IPdfReaderRepository> {
            coEvery { cleanup() } answers { cleanupCalled = true }
        }

        // When
        viewModel.onCleared()

        // Then
        assert(cleanupCalled) { "Cleanup was not called" }
    }

    @Test
    fun `loading new URI should reset current state`() = runTest {
        // Given
        val mockUri1 = mockk<Uri>()
        val mockUri2 = mockk<Uri>()
        declareMock<IPdfReaderRepository> {
            coEvery { loadPdf(any()) } returns Result.success(PdfInfo(1))
        }

        // When
        viewModel.loadPdf(mockUri1)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.loadPdf(mockUri2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val currentState = viewModel.pdfState.value
        assertEquals(PdfLoadingState.Success, currentState.pdfDocumentState.loadingState)
        assertEquals(1u, currentState.pdfDocumentState.pageCount)
    }

    @Test
    fun `loading same URI twice should not trigger reload`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        var loadCount = 0
        declareMock<IPdfReaderRepository> {
            coEvery { loadPdf(mockUri) } answers {
                loadCount++
                Result.success(PdfInfo(1))
            }
        }

        // When
        viewModel.loadPdf(mockUri)
        viewModel.loadPdf(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertEquals(1, loadCount)
    }

    @Test
    fun `loadPdf with invalid URI should handle error`() = runTest {
        // Given
        val mockUri = mockk<Uri>()
        val exception = Exception("Invalid URI")
        declareMock<IPdfReaderRepository> {
            coEvery { loadPdf(mockUri) } returns Result.failure(exception)
        }

        // When
        viewModel.loadPdf(mockUri)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val currentState = viewModel.pdfState.value
        assert(currentState.pdfDocumentState.loadingState is PdfLoadingState.Error)
    }

    @Test
    fun `initial pdfState should be in Idle state`() {
        // Then
        val currentState = viewModel.pdfState.value
        assertEquals(PdfLoadingState.Initial, currentState.pdfDocumentState.loadingState)
        assertEquals(0u, currentState.pdfDocumentState.pageCount)
        assertEquals(PdfLoadingState.Initial, currentState.currentPageState.loadingState)
        assertEquals(0u, currentState.currentPageState.pageNumber)
    }
}
