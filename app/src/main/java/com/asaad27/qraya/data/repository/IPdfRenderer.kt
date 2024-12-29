package com.asaad27.qraya.data.repository

interface IPdfRenderer {
    val pageCount: Int
    fun openPage(index: Int): IPdfPage
    fun close()
}