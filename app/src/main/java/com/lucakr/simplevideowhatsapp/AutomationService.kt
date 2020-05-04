package com.lucakr.simplevideowhatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_END_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_FULLSCREEN_ANSWER_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_FULLSCREEN_DECLINE_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_NOTIFICATION_ANSWER_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_NOTIFICATION_DECLINE_BTN_ACTION


class AutomationService : AccessibilityService() {
    private var notification: Notification?= null
    private lateinit var endCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var callStatus: List<AccessibilityNodeInfoCompat>

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun onReceive(context: Context, intent: Intent) {
            // Check the intent is for us
            when(intent.action) {
                END_CALL -> {
                    // Check endCallBtn to be sure
                    if (endCallBtn.isNotEmpty()) {
                        println("Performing end call action")

                        // Disable touch by enabling TouchExplorationMode
//                        val info = this@AutomationService.serviceInfo
//                        info.flags =
//                            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
//                        this@AutomationService.serviceInfo = info

                        // Need this delay for some reason
                        Thread.sleep(1000)

                        println(endCallBtn.toString())

                        // Click to end call
                        endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        sendErrorNoEndCallBtn()
                    }
                }

                ANSWER_CALL -> {
                    if(notification != null) {
                        println("Answering call")
                        //println(notification!!.toString())
                        notification!!.actions[1].actionIntent.send()
                    } else {
                        sendErrorNoNotification()
                    }
                }

                DECLINE_CALL -> {
                    if(notification != null) {
                        println("Declining call")
                        notification!!.actions[0].actionIntent.send()
                    } else {
                        sendErrorNoNotification()
                    }
                }
            }
        }
    }

    private fun sendErrorNoEndCallBtn() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ERROR_NO_END_CALL_BTN))
    }

    private fun sendErrorNoNotification() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ERROR_NO_NOTIFICATION))
    }

    override fun onCreate() {
        println("AUTOMATION SERVICE STARTED")

        // Setup Broadcast Receiver
        val filter = IntentFilter(END_CALL).apply {
            addAction(ANSWER_CALL)
            addAction(DECLINE_CALL)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onInterrupt() {

    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event!!.packageName == "com.whatsapp" && (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {

            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)

            val tmpEndBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
            callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")

            if (tmpEndBtn.isNotEmpty()) {
                endCallBtn = tmpEndBtn
            }

            if(callStatus.isNotEmpty()) {
                when(callStatus[0].text) {
                    "RINGING" -> {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_CALLING))
                    }
                    "CALLING" -> {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_CALLING))
                    }
                    "Call declined" -> {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_CALL_DECLINED))
                    }
                    "Not answered" -> {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_NOT_ANSWERED))
                    }
                    "" -> {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_IN_CALL))
                    }
                    "WhatsApp video call" -> {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_INCOMING))
                    }
                }
            }

        } else if(event.packageName == "com.whatsapp" && event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val tmpNot = event.parcelableData as Notification

            // Check the channel is correct
            if (tmpNot!!.channelId == "voip_notification_11") {
                notification = tmpNot
            }
        } else if(event.packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            println(event.toString())
            // Always close the shade
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            // Check a notification is active
//            if(notification != null) {
//                // Notification has disappeared
//                notification = null
//                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(WHATSAPP_NOTIFICATION_APPEAR))
//            }
        } else {
            //println(event!!.toString())
            return
        }

    }

    companion object {
        private val AUTOMATION_TAG = AutomationService::class.java.simpleName
        const val END_CALL = "whatsapp_end_call"
        const val ANSWER_CALL = "whatsapp_answer_call_via_notification"
        const val DECLINE_CALL = "whatsapp_decline_call_via_notification"
        const val WINDOW_CHANGE_W_END_CALL = "whatsapp_window_change_with_end_call"
        const val WINDOW_CHANGE_NO_END_CALL = "whatsapp_window_change_without_end_call"
        const val WINDOW_CHANGE = "whatsapp_window_change"
        const val NOTIFICATION_CHANGE = "whatsapp_notification_change"
        const val ERROR_NO_END_CALL_BTN = "whatsapp_no_end_call_btn"
        const val ERROR_NO_NOTIFICATION = "whatsapp_no_notification"
        const val WHATSAPP_CALLING = "whatsapp_calling"
        const val WHATSAPP_IN_CALL = "whatsapp_in_call"
        const val WHATSAPP_CALL_DECLINED = "whatsapp_call_declined"
        const val WHATSAPP_INCOMING = "whatsapp_incoming"
        const val WHATSAPP_NOT_ANSWERED = "whatsapp_not_answered"
    }
}