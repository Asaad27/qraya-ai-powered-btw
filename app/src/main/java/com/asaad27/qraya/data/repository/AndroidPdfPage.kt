package com.asaad27.qraya.data.repository

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer

class AndroidPdfPage(private val page: PdfRenderer.Page) : IPdfPage {
    override val width: Int get() = page.width
    override val height: Int get() = page.height

    override fun render(bitmap: Bitmap, dest: Rect?, transform: Matrix?, renderMode: Int) {
        page.render(bitmap, dest, transform, renderMode)
    }

    override fun close() = page.close()
}