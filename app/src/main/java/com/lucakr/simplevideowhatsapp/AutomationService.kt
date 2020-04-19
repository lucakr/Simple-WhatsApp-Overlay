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
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.FULLSCREEN_ACTIVE
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_ANSWER_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_DECLINE_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_DESTROY_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_END_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_POST_ACCEPT_CALL
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_POST_DESTROY


class AutomationService : AccessibilityService() {
    private var whatsappOpen: Boolean = false
    private var whatsappNotificationAppeared: Boolean = false
    private var notification: Notification?= null
    private lateinit var overlaySvc: Intent
    private lateinit var endCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var acceptCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var declineCallBtn: List<AccessibilityNodeInfoCompat>
    private val mainContext = this

    enum class State {
        CLOSED, PENDING_CLOSURE, CALLING, ACTIVE, INCOMING, UNANSWERED
    }

    private var whatsappState = State.CLOSED

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
            if(intent.action == OVERLAY_END_BTN_ACTION)
            {
                // Check endCallBtn to be sure
                if(endCallBtn.isNotEmpty()) {
                    println("Ending call")

                    // Disable touch by enabling TouchExplorationMode
                    val info = this@AutomationService.serviceInfo
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                    this@AutomationService.serviceInfo = info

                    // Need this delay for some reason
                    Thread.sleep(1000)

                    // Click to end call
                    endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            } else if(intent.action == OVERLAY_ANSWER_BTN_ACTION) {
                // Check acceptCallBtn to be sure
                if (acceptCallBtn.isNotEmpty()) {
                    println("Answering call")

                    // Disable touch by enabling TouchExplorationMode
                    val info = this@AutomationService.serviceInfo
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                    this@AutomationService.serviceInfo = info

                    // Random needed delay
                    Thread.sleep(500)

                    // Enable touch by disabling TouchExplorationMode
                    info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                    this@AutomationService.serviceInfo = info

                    // Accept call
                    val bound = Rect()
                    acceptCallBtn[0].getBoundsInScreen(bound)
                    swipeUpFromPoint(bound.centerX().toFloat(), bound.centerY().toFloat(), object: GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)

                            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                            this@AutomationService.serviceInfo = info

                            // Delay for the active call window to start
                            Thread.sleep(500)

                            // Search the active window for the end button
                            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)
                            endCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
                            println(endCallBtn.toString())

                            // Make the end call overlay appear
                            val subIntent = Intent(OVERLAY_POST_ACCEPT_CALL)
                            LocalBroadcastManager.getInstance(mainContext).sendBroadcast(subIntent)

                            info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                            this@AutomationService.serviceInfo = info
                        }
                    })

                }
            } else if(intent.action == OVERLAY_DECLINE_BTN_ACTION) {
                // Check declineCallBtn to be sure
                if (declineCallBtn.isNotEmpty()) {
                    println("Declining call")

                    // Disable touch by enabling TouchExplorationMode
                    val info = this@AutomationService.serviceInfo
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                    this@AutomationService.serviceInfo = info

                    // Random needed delay
                    Thread.sleep(500)

                    // Enable touch by disabling TouchExplorationMode
                    info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                    this@AutomationService.serviceInfo = info

                    // Decline call
                    val bound = Rect()
                    declineCallBtn[0].getBoundsInScreen(bound)
                    swipeUpFromPoint(bound.centerX().toFloat(), bound.centerY().toFloat(), object: GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            println("Swipe completed")
                            // Disable touch by enabling TouchExplorationMode
                            val info = this@AutomationService.serviceInfo
                            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                            this@AutomationService.serviceInfo = info
                        }
                    })
                }
            } else if(intent.action == OVERLAY_POST_DESTROY) {
                println("Post destroy received")
                stopService(overlaySvc)

            } else if(intent.action == FULLSCREEN_ACTIVE) {

                if(whatsappOpen) {
                    // Enable touch by disabling TouchExplorationMode
                    val info = this@AutomationService.serviceInfo
                    info.flags =
                        info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                    this@AutomationService.serviceInfo = info

                    // Destroy Overlay Service
                    val intent = Intent(OVERLAY_DESTROY_ACTION)
                    LocalBroadcastManager.getInstance(mainContext).sendBroadcast(intent)

                    whatsappOpen = false
                    println("Whatsapp closed")
                }
            }
        }
    }

    override fun onCreate() {
        println("AUTOMATION SERVICE STARTED")
        whatsappOpen = false
        whatsappNotificationAppeared = false
        overlaySvc = Intent(this, OverlayService::class.java)

        // Setup Broadcast Receiver
        val filter = IntentFilter(OVERLAY_POST_DESTROY).apply {
            addAction(OVERLAY_END_BTN_ACTION).apply {
                addAction(OVERLAY_ANSWER_BTN_ACTION).apply {
                    addAction(OVERLAY_DECLINE_BTN_ACTION).apply {
                        addAction(FULLSCREEN_ACTIVE)
                    }
                }
            }
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
        // For some reason filtering packageName="com.whatsapp" in accessibility_service_config.xml doesn't let through notification state changes
        // So we do the package check here instead

        if(event!!.packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && whatsappNotificationAppeared) {
            // Need this delay for some reason (500ms doesnt work but 1000 does)
            Thread.sleep(1000)

            // Enable touch by disabling TouchExplorationMode
            val info = this@AutomationService.serviceInfo
            info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
            this@AutomationService.serviceInfo = info

            // Get display width each time in case orientation has changed
            val displayMetrics = DisplayMetrics()
            var wm: WindowManager? = getSystemService(Context.WINDOW_SERVICE) as WindowManager?
            wm!!.defaultDisplay.getMetrics(displayMetrics)

            var width = displayMetrics.widthPixels
            var height = displayMetrics.heightPixels

            clickPoint(width.toFloat()/2,150f)
        }

        if(event!!.packageName != "com.whatsapp") {
            return
        }

        if(event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)

            when(whatsappState) {
                State.CLOSED -> {
                    // Something in whatsapp has just opened
                    // It should be either a CALLING or INCOMING
                    val callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")

                    if(callStatus.isNotEmpty() && (callStatus[0].text == "CALLING" || callStatus[0].text == "RINGING")) {
                        // Start the calling overlay

                        whatsappState = State.CALLING
                    } else {
                        // Start the INCOMING overlay

                        whatsappState = State.INCOMING
                    }
                }

                State.CALLING -> {
                    // Can only mean we've closed or gone to active
                    val callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")
                    endCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")

                    if(callStatus.isEmpty()) {
                        if (endCallBtn.isEmpty()) {
                            whatsappState = State.PENDING_CLOSURE
                        } else {
                            whatsappState = State.ACTIVE
                        }
                    } else {
                        println("Unknown occurance")
                    }
                }

                State.ACTIVE -> {

                }

                State.INCOMING -> {

                }

                State.UNANSWERED -> {

                }

                else -> {
                    println("Shouldn't get here")
                    whatsappState = State.CLOSED
                }
            }

            val nodeInfoList2 = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)

            if(nodeInfoList2.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn").isNotEmpty()) {
                state = "active"
            } else if(nodeInfoList2.findAccessibilityNodeInfosByViewId("com.whatsapp:id/accept_incoming_call_view").isNotEmpty() && nodeInfoList2.findAccessibilityNodeInfosByViewId("com.whatsapp:id/decline_incoming_call_view").isNotEmpty()) {
                state = "incoming"
            } else if(nodeInfoList2.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_back_btn").isNotEmpty()) {
                state = "unanswered"
            }

            println(state.toString())

        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !whatsappOpen) {
             // Try to get the end_call_btn
            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)
            endCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
            acceptCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/accept_incoming_call_view")
            declineCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/decline_incoming_call_view")

            if (endCallBtn.isNotEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty()) {
                // Start END_CALL overlay
                overlaySvc.removeExtra("state")
                overlaySvc.putExtra("state", "end")
                stopService(overlaySvc)
                startService(overlaySvc)
            } else if (endCallBtn.isEmpty() && acceptCallBtn.isNotEmpty() && declineCallBtn.isNotEmpty()) {
                // Start ANSWER_CALL overlay
                overlaySvc.removeExtra("state")
                overlaySvc.putExtra("state", "start")
                stopService(overlaySvc)
                startService(overlaySvc)
            } else if (endCallBtn.isEmpty() && acceptCallBtn.isEmpty() && declineCallBtn.isEmpty()) {
                println("Whatsapp closing event")
                return
            } else {
                // Ideally in our controlled state, this should never occur
                // Need to figure out a way to handle it if it does occur (global back button might be a way)
                println("ERR: Available nodes don't make sense")
                return
            }

            // Whatsapp is now open in some manner
            whatsappOpen = true

            return
        } else if(event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            println("WHATSAPP NOTIFICATION")
            notification = event.parcelableData as Notification
            if(notification!!.channelId != "voip_notification_11") {
                return
            }

            whatsappNotificationAppeared = true

            // Disable touch by enabling TouchExplorationMode
            val info = this@AutomationService.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            this@AutomationService.serviceInfo = info
        } else {
            return
        }

    }

    companion object {
        private val AUTOMATION_TAG = AutomationService::class.java.simpleName
    }
}