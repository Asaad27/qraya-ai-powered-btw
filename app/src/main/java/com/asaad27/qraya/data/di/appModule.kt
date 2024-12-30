package com.asaad27.qraya.data.di

import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import com.asaad27.qraya.data.repository.AndroidPdfRenderer
import com.asaad27.qraya.data.repository.IPdfReaderRepository
import com.asaad27.qraya.data.repository.PdfReaderRepository
import com.asaad27.qraya.ui.pdf.viewmodel.PdfViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<IPdfReaderRepository> {
        PdfReaderRepository(
            androidApplication(),
            Dispatchers.IO,
            { AndroidPdfRenderer(PdfRenderer(it)) },
            { Matrix() }
        )
    }
    single { androidContext() }
    viewModelOf(::PdfViewModel)
}