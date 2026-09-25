package com.agentchat.lite.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agentchat.lite.di.AppContainer

/**
 * [ChatViewModel] 的工厂，注入 [AppContainer]。
 */
class ChatViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(container) as T
    }
}
