package com.asaad27.qraya.data.di

import com.asaad27.qraya.data.repository.IPdfReaderRepository
import com.asaad27.qraya.data.repository.PdfReaderRepository
import com.asaad27.qraya.ui.pdf.viewmodel.PdfViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { androidContext() }
    singleOf(::PdfReaderRepository) { bind<IPdfReaderRepository>() }
    viewModel { PdfViewModel(get()) }
}