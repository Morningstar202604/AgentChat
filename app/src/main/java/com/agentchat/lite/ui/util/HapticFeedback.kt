package com.agentchat.lite.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * 轻震动反馈封装。按钮点击、长按等交互触发短震动，提升触感反馈。
 */
object HapticFeedback {

    /** 在当前 Compose 视图上触发一次轻量震动。 */
    @Composable
    fun rememberHaptic(): () -> Unit {
        val view = LocalView.current
        return {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** 直接在指定 View 上触发震动（非 Composable 场景）。 */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** 长按反馈（稍强）。 */
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
