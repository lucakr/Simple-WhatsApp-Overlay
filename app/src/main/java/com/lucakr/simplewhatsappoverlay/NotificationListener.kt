package com.lucakr.simplewhatsappoverlay

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.ANSWER
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.DECLINE
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.RESET
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.END
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.ERROR
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.ACTIVE_POST
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.ACTIVE_REM
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.INCOMING_POST
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.INCOMING_REM
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.INFO_CALLER
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.OUTGOING_POST
import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.OUTGOING_REM
//import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.DECLINED_POST
//import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.DECLINED_REM
//import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.MISSED_POST
//import com.lucakr.simplewhatsappoverlay.AccessibilityService.Companion.MISSED_REM

class NotificationListener : NotificationListenerService() {
    private val nlContext = this
    private var answer: Notification.Action? = null
    private var decline: Notification.Action? = null
    private var end: Notification.Action? = null

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                RESET    -> nlContext.cancelAllNotifications()
                ANSWER   -> answer?.actionIntent?.send() ?: error()
                DECLINE  -> decline?.actionIntent?.send() ?: error()
                END      -> end?.actionIntent?.send() ?: error()
            }
        }
    }

    private fun error() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ERROR))
    }

    override fun onCreate() {
        super.onCreate()
        println("Notification Listener Active")

        // Setup Broadcast Receiver
        val filter = IntentFilter(RESET).apply {
            addAction(ANSWER)
            addAction(DECLINE)
            addAction(END)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        println("Notification Listener Connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Filter whatsapp
        if(sbn.packageName != "com.whatsapp") return

        // Get the notification trigger from the notification text
        val notificationTrigger = with(sbn.notification.extras.getString("android.text")) {
            when {
                this?.contains("Incoming") == true -> INCOMING_POST
                //this?.contains("Declined") == true -> DECLINED_POST
                this?.contains("Ongoing") == true -> ACTIVE_POST
                this?.contains("Ringing") == true -> OUTGOING_POST
                this?.contains("Calling") == true -> OUTGOING_POST
                //this?.contains("Missed") == true -> MISSED_POST
                else -> return
            }
        }

        // Get the actions
        answer = sbn.notification.actions!!.find{it.title.contains("Answer")}
        decline = sbn.notification.actions!!.find{it.title.contains("Decline")}
        end = sbn.notification.actions!!.find{it.title.contains("Hang up")}

        // Send notification posted intent with caller name
        val intent = Intent(notificationTrigger)
        intent.putExtra(INFO_CALLER, sbn.notification.extras!!.getString("android.title"))

        println("TX $intent")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Filter whatsapp
        if(sbn.packageName != "com.whatsapp") return

        // Get the notification trigger from the notification text
        val notificationTrigger = with(sbn.notification.extras.getString("android.text")) {
            when {
                this?.contains("Incoming") == true -> INCOMING_REM
                //this?.contains("Declined") == true -> DECLINED_REM
                this?.contains("Ongoing") == true -> ACTIVE_REM
                this?.contains("Ringing") == true -> OUTGOING_REM
                this?.contains("Calling") == true -> OUTGOING_REM
                //this?.contains("Missed") == true -> MISSED_REM
                else -> return
            }
        }

        // Send notification removed intent
        val intent = Intent(notificationTrigger)
        println("TX $intent")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}