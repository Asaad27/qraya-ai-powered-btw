package com.asaad27.qraya

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.xr.compose.platform.LocalHasXrSpatialFeature
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.EdgeOffset
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterEdge
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import com.asaad27.qraya.ui.theme.QrayaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QrayaTheme {
                val session = LocalSession.current
                if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
                    Subspace {
                        MySpatialContent(onRequestHomeSpaceMode = { session?.requestHomeSpaceMode() })
                    }
                } else {
                    My2DContent(onRequestFullSpaceMode = { session?.requestFullSpaceMode() })
                }
            }
        }
    }
}

sealed class PdfLoadingState {
    data object Initial : PdfLoadingState()
    data object Loading : PdfLoadingState()
    data class Success(val pageCount: Int) : PdfLoadingState()
    data class Error(val message: String, val exception: Exception) : PdfLoadingState()
}

data class PdfUiState(
    val loadingState: PdfLoadingState = PdfLoadingState.Initial,
    val pageCount: Int = 0,
    val currentPage: Int = 0
)

class PdfViewModel(
    private val applicationContext: Context,
    private var uri: Uri
) : ViewModel() {

    private val _pdfState = MutableStateFlow(PdfUiState())
    val pdfState = _pdfState.asStateFlow()


    private var fileDesc: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    init {
        loadPdf(this.uri)
    }

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                _pdfState.update {
                    it.copy(
                        loadingState = PdfLoadingState.Loading,
                        pageCount = 0,
                        currentPage = 0
                    )
                }

                withContext(Dispatchers.IO) {
                    fileDesc = applicationContext.contentResolver.openFileDescriptor(uri, "r")?.also {
                        renderer = PdfRenderer(it)
                    } ?: throw Exception("file descriptor is null")
                }

                renderer?.let { pdfRenderer ->
                    _pdfState.update {
                        it.copy(
                            loadingState = PdfLoadingState.Success(pdfRenderer.pageCount),
                            pageCount = pdfRenderer.pageCount,
                            currentPage = 0
                        )
                    }
                }

            } catch (e:  Exception) {
                _pdfState.update {
                    it.copy(
                        loadingState = PdfLoadingState.Error("error during the initialization of the pdf renderer: ${e.message}", e),
                        pageCount = renderer ?.pageCount ?: 0,
                        currentPage = 0
                    )
                }
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap = withContext(Dispatchers.IO) {
        renderer?.openPage(pageIndex)?.use { page ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply {
                setScale(width.toFloat() / page.width, height.toFloat() / page.height)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            return@use bitmap
        } ?: throw Exception("error during page rendering, page is null")
    }


    suspend fun renderPagesAsync(pages: List<Int>, width: Int, height: Int): List<Bitmap> = coroutineScope {
        pages.map { pageIndex ->
            async { renderPage(pageIndex, width, height) }
        }.awaitAll()
    }

    fun renderPagesFlow(
        pages: List<Int>,
        width: Int,
        height: Int
    ): Flow<Pair<Int, Bitmap>> = channelFlow {
        pages.forEach { pageIndex ->
            launch {
                val bitmap = renderPage(pageIndex, width, height)
                send(pageIndex to bitmap)
            }
        }
    }.flowOn(Dispatchers.IO)


    override fun onCleared() {
        super.onCleared()
        fileDesc?.close()
        renderer?.close()
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun MySpatialContent(onRequestHomeSpaceMode: () -> Unit) {
    SpatialPanel(SubspaceModifier.width(1280.dp).height(800.dp).resizable().movable()) {
        Surface {
            MainContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp)
            )
        }
        Orbiter(
            position = OrbiterEdge.Top,
            offset = EdgeOffset.inner(offset = 20.dp),
            alignment = Alignment.End,
            shape = SpatialRoundedCornerShape(CornerSize(28.dp))
        ) {
            HomeSpaceModeIconButton(
                onClick = onRequestHomeSpaceMode,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun My2DContent(onRequestFullSpaceMode: () -> Unit) {
    Surface {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MainContent(modifier = Modifier.padding(48.dp))
            if (LocalHasXrSpatialFeature.current) {
                FullSpaceModeIconButton(
                    onClick = onRequestFullSpaceMode,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.hello_android_xr), modifier = modifier)
}

@Composable
fun FullSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_full_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_full_space_mode)
        )
    }
}

@Composable
fun HomeSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_home_space_mode)
        )
    }
}

@PreviewLightDark
@Composable
fun My2dContentPreview() {
    QrayaTheme {
        My2DContent(onRequestFullSpaceMode = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FullSpaceModeButtonPreview() {
    QrayaTheme {
        FullSpaceModeIconButton(onClick = {})
    }
}

@PreviewLightDark
@Composable
fun HomeSpaceModeButtonPreview() {
    QrayaTheme {
        HomeSpaceModeIconButton(onClick = {})
    }
}