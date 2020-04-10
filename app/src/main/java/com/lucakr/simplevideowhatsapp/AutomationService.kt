package com.lucakr.simplevideowhatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplevideowhatsapp.OverlayService
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_END_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_START_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_DESTROY_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_POST_DESTROY
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.FULLSCREEN_ACTIVE


/** NOTE ON WHATSAPP ACCESSIBILITY EVENTS via app
 *
 * ON CALL START:
 * EventType: TYPE_WINDOW_STATE_CHANGED; EventTime: 193470865; PackageName: com.whatsapp; MovementGranularity: 0; Action: 0; ContentChangeTypes: []; WindowChangeTypes: [] [ ClassName: android.widget.FrameLayout; Text: []; ContentDescription: null; ItemCount: -1; CurrentItemIndex: -1; Enabled: true; Password: false; Checked: false; FullScreen: false; Scrollable: false; BeforeText: null; FromIndex: -1; ToIndex: -1; ScrollX: -1; ScrollY: -1; MaxScrollX: -1; MaxScrollY: -1; AddedCount: -1; RemovedCount: -1; ParcelableData: null ]; recordCount: 0
 * EventType: TYPE_WINDOW_STATE_CHANGED; EventTime: 193471077; PackageName: com.whatsapp; MovementGranularity: 0; Action: 0; ContentChangeTypes: []; WindowChangeTypes: [] [ ClassName: com.whatsapp.voipcalling.VoipActivityV2; Text: [Video Call]; ContentDescription: null; ItemCount: -1; CurrentItemIndex: -1; Enabled: true; Password: false; Checked: false; FullScreen: true; Scrollable: false; BeforeText: null; FromIndex: -1; ToIndex: -1; ScrollX: -1; ScrollY: -1; MaxScrollX: -1; MaxScrollY: -1; AddedCount: -1; RemovedCount: -1; ParcelableData: null ]; recordCount: 0
 *
 * ON CALL END:
 * Nothing
 *
 * ON CALL RECEIVE:
 * UNKNOWN
 */

class AutomationService : AccessibilityService() {
    private var whatsappOpen: Boolean = false
    private lateinit var overlaySvc: Intent
    private lateinit var endCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var startCallBtn: List<AccessibilityNodeInfoCompat>
    private val mainContext = this

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Check the intent is for us
            if(intent.action == OVERLAY_END_BTN_ACTION)
            {
                // Check endCallBtn to be sure
                if(endCallBtn.isNotEmpty()) {
                    println("Ending call")



                    // Stop the overlay to allow the click
                    //stopService(overlaySvc)

                    // Disable touch by enabling TouchExplorationMode
                    val info = this@AutomationService.serviceInfo
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                    this@AutomationService.serviceInfo = info

                    Thread.sleep(100)

                    // Click to end call
                    endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)

                    // Restart the overlay in protection mode
                    //overlaySvc.removeExtra("state")
                    //overlaySvc.putExtra("state", "protect")
                    //startService(overlaySvc)
                }
            } else if(intent.action == OVERLAY_START_BTN_ACTION) {
                // Check startCallBtn to be sure
                if(startCallBtn.isNotEmpty()) {

                }
            } else if(intent.action == OVERLAY_POST_DESTROY) {
                stopService(overlaySvc)
            } else if(intent.action == FULLSCREEN_ACTIVE) {
                whatsappOpen = false

                // Enable touch by disabling TouchExplorationMode
                val info = this@AutomationService.serviceInfo
                info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                this@AutomationService.serviceInfo = info

                // Destroy Overlay Service
                val intent = Intent(OVERLAY_DESTROY_ACTION)
                LocalBroadcastManager.getInstance(mainContext).sendBroadcast(intent)
            }
        }
    }

    override fun onCreate() {
        println("AUTOMATION SERVICE STARTED")
        whatsappOpen = false
        overlaySvc = Intent(this, OverlayService::class.java)

        // Setup Broadcast Receiver
        val filter = IntentFilter(OVERLAY_POST_DESTROY).apply {
            addAction(OVERLAY_END_BTN_ACTION).apply {
                addAction(OVERLAY_START_BTN_ACTION).apply {
                    addAction(FULLSCREEN_ACTIVE)
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    override fun onInterrupt() {}

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        //println("ACCESS EVENT")

        if(event!!.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !whatsappOpen)
        {
            // Whatsapp is now open in some manner
            whatsappOpen = true

            // Try to get the end_call_btn
            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(rootInActiveWindow)
            endCallBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")

            if(endCallBtn.isEmpty())
            {
                // Ideally in our controlled state, this should never occur
                // Need to figure out a way to handle it if it does occur (global back button might be a way)
                println("ERR: No end_call_btn")
                return
            } else {
                // Start END_CALL overlay
                println("HERE")
                overlaySvc.removeExtra("state")
                overlaySvc.putExtra("state", "end")
                stopService(overlaySvc)
                startService(overlaySvc)
            }

            return
        } else {
            return
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun answerCall() {
        val builder = GestureDescription.Builder()

        val dragUpPath = Path().apply {
            moveTo(ANSWER_X_START, ANSWER_Y_START)
            lineTo(ANSWER_X_STOP, ANSWER_Y_STOP)
        }
        val dragUpDuration = 10L // 0.01 second

        val stroke = GestureDescription.StrokeDescription(dragUpPath, 0L, dragUpDuration, false)
        builder.addStroke(stroke)

        val gesture = builder.build()

        // FIXME actually check the callbacks
        val isDispatched = dispatchGesture(gesture, null, null)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun declineCall() {
        val builder = GestureDescription.Builder()

        val dragUpPath = Path().apply {
            moveTo(DECLINE_X_START, DECLINE_Y_START)
            lineTo(DECLINE_X_STOP, DECLINE_Y_STOP)
        }
        val dragUpDuration = 10L // 0.01 second

        val stroke = GestureDescription.StrokeDescription(dragUpPath, 0L, dragUpDuration, false)
        builder.addStroke(stroke)

        val gesture = builder.build()

        // FIXME actually check the callbacks
        val isDispatched = dispatchGesture(gesture, null, null)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun endCall() {
        val builder = GestureDescription.Builder()

        val click = Path().apply {
            moveTo(END_X, END_Y)
            lineTo(END_X, END_Y+1)
        }
        val clickDuration = 10L // 0.01 second

        val stroke = GestureDescription.StrokeDescription(click, 0L, clickDuration, false)
        builder.addStroke(stroke)

        val gesture = builder.build()

        // FIXME actually check the callbacks
        val isDispatched = dispatchGesture(gesture, null, null)
    }

    companion object {
        private val AUTOMATION_TAG = AutomationService::class.java.simpleName

        private val ANSWER_X_START = 500F
        private val ANSWER_Y_START = 0F
        private val ANSWER_X_STOP = 400F
        private val ANSWER_Y_STOP = 0F

        private val DECLINE_X_START = 500F
        private val DECLINE_Y_START = -200F
        private val DECLINE_X_STOP = 400F
        private val DECLINE_Y_STOP = -200F

        private val END_X = 400F
        private val END_Y = 0F
    }
}