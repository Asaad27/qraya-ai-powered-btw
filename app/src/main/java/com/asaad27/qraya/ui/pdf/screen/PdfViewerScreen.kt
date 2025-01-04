package com.asaad27.qraya.ui.pdf.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import com.asaad27.qraya.ui.pdf.model.PdfLoadingState
import com.asaad27.qraya.ui.pdf.model.PdfPageState
import com.asaad27.qraya.ui.pdf.viewmodel.PdfViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PdfViewerScreen(
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = koinViewModel()
) {
    val pdfState by viewModel.pdfState.collectAsState()

    when (pdfState.pdfDocumentState.loadingState) {
        PdfLoadingState.Initial -> PdfInitialState(
            modifier = modifier,
            onLoadPdf = { viewModel.loadPdf(it) }
        )
        PdfLoadingState.Loading -> PdfLoadingState(modifier = modifier)
        is PdfLoadingState.Error -> PdfErrorState(
            modifier = modifier,
            error = (pdfState.pdfDocumentState.loadingState as PdfLoadingState.Error).message
        )
        PdfLoadingState.Success -> PdfContentState(
            modifier = modifier,
            currentPageState = pdfState.currentPageState,
            onRenderPage = { page, width, height ->
                viewModel.renderPage(page.toInt(), width, height)
            }
        )
    }
}

@Composable
fun PdfInitialState(
    modifier: Modifier = Modifier,
    onLoadPdf: (Uri) -> Unit
) {
    val choosePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(onLoadPdf)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { choosePdfLauncher.launch("application/pdf") }) {
            Text("Select PDF")
        }
    }
}

@Composable
fun PdfLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun PdfErrorState(
    modifier: Modifier = Modifier,
    error: String
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Error: $error")
    }
}

@Composable
fun PdfContentState(
    modifier: Modifier = Modifier,
    currentPageState: PdfPageState,
    onRenderPage: suspend (UInt, Int, Int) -> Bitmap
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var currentPage by remember { mutableStateOf<Bitmap?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
    ) {
        LaunchedEffect(currentPageState.pageNumber, size.width, size.height) {
            if (size.width > 0 && size.height > 0) {
                currentPage = onRenderPage(
                    currentPageState.pageNumber,
                    size.width,
                    size.height
                )
            }
        }

        when (currentPageState.loadingState) {
            PdfLoadingState.Loading -> PdfLoadingState()
            is PdfLoadingState.Error -> PdfErrorState(
                error = currentPageState.loadingState.message
            )
            PdfLoadingState.Success -> currentPage?.let { bitmap ->
                PdfPage(page = bitmap)
            }
            PdfLoadingState.Initial -> Unit
        }
    }
}

@Composable
private fun PdfPage(
    modifier: Modifier = Modifier,
    page: Bitmap
) {
    AsyncImage(
        modifier = modifier.fillMaxWidth(),
        model = if (LocalInspectionMode.current) TestModels.previewBitmap else page,
        contentDescription = "PDF page",
        contentScale = ContentScale.FillWidth
    )
}

private object TestModels {
    val previewBitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(Color.LTGRAY)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 50f
        }
        canvas.drawText("Preview PDF Page", 100f, 400f, paint)
    }
}

@Preview
@Composable
private fun PdfPagePreview() {
    val mockBitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
    PdfPage(page = mockBitmap)
}

@Preview(showBackground = true)
@Composable
private fun PdfInitialStatePreview() {
    PdfInitialState(onLoadPdf = {})
}

@Preview(showBackground = true)
@Composable
private fun PdfLoadingStatePreview() {
    PdfLoadingState()
}

@Preview(showBackground = true)
@Composable
private fun PdfErrorStatePreview() {
    PdfErrorState(error = "Failed to load PDF")
}

@Preview(showBackground = true)
@Composable
private fun PdfContentStatePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PdfPage(page = TestModels.previewBitmap)
    }
}