package com.asaad27.qraya.data.repository

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect

interface IPdfPage : AutoCloseable {
    val width: Int
    val height: Int
    fun render(bitmap: Bitmap, dest: Rect?, transform: Matrix?, renderMode: Int)
}