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
    private var whatsappOpen: Boolean = false
    private var whatsappNotificationAppeared: Boolean = false
    private var notification: Notification?= null
    private var lastValidNotification: Notification?=null
    private lateinit var endCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var callBackBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var acceptCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var declineCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var callStatus: List<AccessibilityNodeInfoCompat>
    private val mainContext = this

    @RequiresApi(Build.VERSION_CODES.N)
    private fun clickPoint(x: Float, y: Float) {
        val dragUpPath = Path().apply {
            moveTo(x, y)
            lineTo(x, y+1)
        }
        val duration = 1L // 100L = 0.1 second

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(dragUpPath, 0L, duration))
        dispatchGesture(gestureBuilder.build(), object: GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                println("Click completed")
            }
        }, null)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun swipeUpFromPoint(x: Float, y: Float, callback: GestureResultCallback) {
        val dragUpPath = Path().apply {
            moveTo(x, y)
            lineTo(x, y-500)
        }
        val duration = 10L // 100L = 0.1 second

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(dragUpPath, 0L, duration))
        dispatchGesture(gestureBuilder.build(), callback, null)
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun onReceive(context: Context, intent: Intent) {
            // Check the intent is for us
            when(intent.action) {
                OVERLAY_END_BTN_ACTION -> {
                    // TODO check this works

                    // Check endCallBtn to be sure
                    if (endCallBtn.isNotEmpty()) {
                        println("Ending call")

                        // Disable touch by enabling TouchExplorationMode
                        val info = this@AutomationService.serviceInfo
                        info.flags =
                            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                        this@AutomationService.serviceInfo = info

                        // Need this delay for some reason
                        Thread.sleep(1000)

                        // Click to end call
                        endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }

                OVERLAY_FULLSCREEN_ANSWER_BTN_ACTION -> {
                    // TODO fix this for older Android versions

//                    // Check acceptCallBtn to be sure
//                    if (acceptCallBtn.isNotEmpty()) {
//                        println("Answering call")
//
//                        // Disable touch by enabling TouchExplorationMode
//                        val info = this@AutomationService.serviceInfo
//                        info.flags =
//                            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
//                        this@AutomationService.serviceInfo = info
//
//                        // Random needed delay
//                        Thread.sleep(500)
//
//                        // Enable touch by disabling TouchExplorationMode
//                        info.flags =
//                            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
//                        this@AutomationService.serviceInfo = info
//
//                        // Accept call
//                        val bound = Rect()
//                        acceptCallBtn[0].getBoundsInScreen(bound)
//                        swipeUpFromPoint(
//                            bound.centerX().toFloat(),
//                            bound.centerY().toFloat(),
//                            object : GestureResultCallback() {
//                                override fun onCompleted(gestureDescription: GestureDescription?) {
//                                    super.onCompleted(gestureDescription)
//
//                                    info.flags =
//                                        info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
//                                    this@AutomationService.serviceInfo = info
//
//                                    // Delay for the active call window to start
//                                    Thread.sleep(500)
//
//                                    // Search the active window for the end button
//                                    val nodeInfoList =
//                                        AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)
//                                    endCallBtn =
//                                        nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
//                                    println(endCallBtn.toString())
//
//                                    // Make the end call overlay appear
//                                    val subIntent = Intent(OVERLAY_POST_ACCEPT_CALL)
//                                    LocalBroadcastManager.getInstance(mainContext)
//                                        .sendBroadcast(subIntent)
//
//                                    info.flags =
//                                        info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
//                                    this@AutomationService.serviceInfo = info
//                                }
//                            })
//
//                    }
                }

                OVERLAY_FULLSCREEN_DECLINE_BTN_ACTION -> {
                    // TODO fix this for older Android versions

//                    // Check declineCallBtn to be sure
//                    if (declineCallBtn.isNotEmpty()) {
//                        println("Declining call")
//
//                        // Disable touch by enabling TouchExplorationMode
//                        val info = this@AutomationService.serviceInfo
//                        info.flags =
//                            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
//                        this@AutomationService.serviceInfo = info
//
//                        // Random needed delay
//                        Thread.sleep(500)
//
//                        // Enable touch by disabling TouchExplorationMode
//                        info.flags =
//                            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
//                        this@AutomationService.serviceInfo = info
//
//                        // Decline call
//                        val bound = Rect()
//                        declineCallBtn[0].getBoundsInScreen(bound)
//                        swipeUpFromPoint(
//                            bound.centerX().toFloat(),
//                            bound.centerY().toFloat(),
//                            object : GestureResultCallback() {
//                                override fun onCompleted(gestureDescription: GestureDescription?) {
//                                    super.onCompleted(gestureDescription)
//                                    println("Swipe completed")
//                                    // Disable touch by enabling TouchExplorationMode
//                                    val info = this@AutomationService.serviceInfo
//                                    info.flags =
//                                        info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
//                                    this@AutomationService.serviceInfo = info
//                                }
//                            })
//                    }
                }

                OVERLAY_NOTIFICATION_ANSWER_BTN_ACTION -> {
                    println("Attempting to answer")
                    //println(notification!!.toString())
                    lastValidNotification!!.actions[1].actionIntent.send()
                }

                OVERLAY_NOTIFICATION_DECLINE_BTN_ACTION -> {
                    println("Attempting to decline")
                    lastValidNotification!!.actions[0].actionIntent.send()
                }

                CHECK -> {

                }
            }
        }
    }

    override fun onCreate() {
        println("AUTOMATION SERVICE STARTED")
        whatsappOpen = false
        whatsappNotificationAppeared = false

        // Setup Broadcast Receiver
        val filter = IntentFilter(OVERLAY_END_BTN_ACTION).apply {
            addAction(OVERLAY_NOTIFICATION_ANSWER_BTN_ACTION)
            addAction(OVERLAY_NOTIFICATION_DECLINE_BTN_ACTION)
            addAction(OVERLAY_FULLSCREEN_ANSWER_BTN_ACTION)
            addAction(OVERLAY_FULLSCREEN_DECLINE_BTN_ACTION)
            addAction(CHECK)
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
        if (event!!.packageName == "com.whatsapp" && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            println(event!!.toString())
            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)

            callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")
            endCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
            acceptCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/accept_incoming_call_view")
            declineCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/decline_incoming_call_view")
            callBackBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_back_btn")

            if (endCallBtn.isNotEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty() && callBackBtn.isEmpty() && callStatus.isNotEmpty() && (callStatus[0].text == "RINGING" || callStatus[0].text == "CALLING")) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_CALLING))
            } else if (endCallBtn.isNotEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty() && callBackBtn.isEmpty() && callStatus.isEmpty()) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_CALL_ACCEPTED))
            } else if (endCallBtn.isNotEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty() && callBackBtn.isEmpty() && callStatus.isNotEmpty() && callStatus[0].text == "Call declined") {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_CALL_DECLINED))
            } else if (endCallBtn.isEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty() && callBackBtn.isNotEmpty() && callStatus.isNotEmpty() && callStatus[0].text == "Not answered") {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_UNANSWERED_APPEAR))
            } else if (endCallBtn.isEmpty() && acceptCallBtn.isNotEmpty() && declineCallBtn.isNotEmpty() && callBackBtn.isEmpty() && callStatus.isEmpty()) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_INCOMING_FULLSCREEN_APPEAR))
            } else if (endCallBtn.isEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty() && callBackBtn.isEmpty() && callStatus.isEmpty()) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_UNDOCUMENTED_VIEW))
            } else {
                // Ideally in our controlled state, this should never occur
                // Need to figure out a way to handle it if it does occur (global back button might be a way)
                println("ERR: Available nodes don't make sense at all")
                return
            }

        } else if(event.packageName == "com.whatsapp" && event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            println("WHATSAPP NOTIFICATION")
            notification = event.parcelableData as Notification
            println(notification!!.actions.toString())

            // Check the channel is correct
            if (notification!!.channelId != "voip_notification_11") {
                notification = null
                return
            }

            if(notification != null) {
                lastValidNotification = notification
            }

            // Notification has appeared
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_INCOMING_NOTIFICATION_APPEAR))
        } else if(event.packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Check a notification is active
            if(notification != null) {
                // Notification has disappeared
                notification = null
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_INCOMING_NOTIFICATION_DISAPPEAR))
            }
        } else {
            //println(event!!.toString())
            return
        }

    }

    companion object {
        private val AUTOMATION_TAG = AutomationService::class.java.simpleName
        const val ACTION_INCOMING_NOTIFICATION_APPEAR = "incoming_notification_appear"
        const val ACTION_INCOMING_NOTIFICATION_DISAPPEAR = "incoming_notification_disappear"
        const val ACTION_INCOMING_FULLSCREEN_APPEAR = "incoming_fullscreen_appear"
        const val ACTION_UNDOCUMENTED_VIEW = "undocumented_view"
        const val ACTION_UNANSWERED_APPEAR = "unanswered_appear"
        const val ACTION_CALLING = "calling"
        const val ACTION_CALL_ACCEPTED = "call_accepted"
        const val ACTION_CALL_DECLINED = "call_declined"
        const val CHECK = "stuff"
    }
}