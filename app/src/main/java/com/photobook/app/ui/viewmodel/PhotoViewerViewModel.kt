package com.photobook.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class PhotoViewerViewModel : ViewModel() {

    val currentIndex = MutableStateFlow(0)

    fun setCurrentIndex(index: Int) {
        currentIndex.update { index }
    }
}
