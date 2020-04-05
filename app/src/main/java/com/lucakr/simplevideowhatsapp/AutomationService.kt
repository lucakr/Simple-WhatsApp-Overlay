package com.lucakr.simplevideowhatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class AutomationService : AccessibilityService() {
    override fun onCreate() {
        serviceInfo.flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    @RequiresApi(Build.VERSION_CODES.O)
    private fun answerCall() {
        val dragUpPath = Path().apply {
            moveTo(ANSWER_X_START, ANSWER_Y_START)
            lineTo(ANSWER_X_STOP, ANSWER_Y_STOP)
        }
        val dragUpDuration = 10L // 0.01 second

        GestureDescription.StrokeDescription(dragUpPath, 0L, dragUpDuration, false).apply {  }
    }

    private fun declineCall() {

    }

    private fun endCall() {

    }

    companion object {
        private val AUTOMATION_TAG = AutomationService::class.java.simpleName

        private val ANSWER_X_START = 500F
        private val ANSWER_Y_START = 0F
        private val ANSWER_X_STOP = 400F
        private val ANSWER_Y_STOP = 0F

        private val DECLINE_X = 500F
        private val DECLINE_Y = -200F

        private val END_X = 400F
        private val END_Y = 0F
    }
}