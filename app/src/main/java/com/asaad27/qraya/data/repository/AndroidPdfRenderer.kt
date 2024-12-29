package com.asaad27.qraya.data.repository

import android.graphics.pdf.PdfRenderer

class AndroidPdfRenderer(private val pdfRenderer: PdfRenderer) : IPdfRenderer {
    override val pageCount: Int get() = pdfRenderer.pageCount

    override fun openPage(index: Int): IPdfPage = AndroidPdfPage(pdfRenderer.openPage(index))

    override fun close() = pdfRenderer.close()
}