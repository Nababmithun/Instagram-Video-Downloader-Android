package com.example.instagramvideodownloaderapp.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(cls: Class<T>): T {
        if (cls.isAssignableFrom(MainViewModel::class.java)) {
           // @Suppress("UNCHECKED_CAST")
            //return MainViewModel(ctx.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
