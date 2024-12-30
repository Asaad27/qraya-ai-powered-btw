package com.asaad27.qraya.ui.pdf.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import coil3.compose.AsyncImage
import com.asaad27.qraya.ui.pdf.model.PdfLoadingState
import com.asaad27.qraya.ui.pdf.viewmodel.PdfViewModel
import org.koin.androidx.compose.koinViewModel

//todo: this is just an example, fix later

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PdfViewerScreen(
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = koinViewModel()
) {
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val pdfState by viewModel.pdfState.collectAsState()

    val choosePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) {
        pdfUri = it
    }

    LaunchedEffect(pdfUri) {
        pdfUri?.let { uri ->
            viewModel.loadPdf(uri)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = constraints.maxWidth
        val boxHeight = constraints.maxHeight

        LaunchedEffect(pdfState.pdfDocumentState.loadingState, boxWidth, boxHeight) {
            if (pdfState.pdfDocumentState.loadingState is PdfLoadingState.Success &&
                boxWidth > 0 && boxHeight > 0
            ) {
                pdfUri?.let {
                    renderedPages = viewModel.renderPagesAsync(
                        pages = (0 until pdfState.pdfDocumentState.pageCount.toInt()).toList(),
                        width = boxWidth,
                        height = boxHeight
                    )
                }
            }
        }


        if (pdfUri == null) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        choosePdfLauncher.launch("application/pdf")
                    }
                ) {
                    Text("load the PiDi F madafaka")
                }
            }
        } else {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (pdfState.pdfDocumentState.loadingState) {
                    is PdfLoadingState.Loading -> {
                        CircularProgressIndicator()
                    }

                    is PdfLoadingState.Error -> {
                        Text("Error: ${(pdfState.pdfDocumentState.loadingState as PdfLoadingState.Error).message}")
                    }

                    is PdfLoadingState.Success -> {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(renderedPages) { page ->
                                PdfPage(page = page)
                            }
                        }
                    }

                    PdfLoadingState.Initial -> Text("there is nothing yet madafaka")
                }

            }
        }
    }
}

@Composable
fun PdfPage(
    modifier: Modifier = Modifier,
    page: Bitmap
) {
    AsyncImage(
        modifier = modifier.fillMaxWidth(),
        model = page,
        contentDescription = "9ra w zid 9ra",
    )
}