package com.asaad27.qraya.data.repository

interface IPdfRenderer: AutoCloseable {
    val pageCount: Int
    fun openPage(index: Int): IPdfPage
}