package com.lucakr.simplevideowhatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_END_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_START_BTN_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_DESTROY_ACTION
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.OVERLAY_POST_DESTROY
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.FULLSCREEN_ACTIVE

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

                    // Disable touch by enabling TouchExplorationMode
                    val info = this@AutomationService.serviceInfo
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                    this@AutomationService.serviceInfo = info

                    // Need this delay for some reason
                    Thread.sleep(100)

                    // Click to end call
                    endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    companion object {
        private val AUTOMATION_TAG = AutomationService::class.java.simpleName
    }
}