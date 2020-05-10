package com.lucakr.simplevideowhatsapp

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager


@RequiresApi(Build.VERSION_CODES.P)
class NotificationListener : NotificationListenerService() {
    private val nlContext = this

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {
            nlContext.cancelAllNotifications()
        }
    }

    override fun onCreate() {
        super.onCreate()
        println("Notification Listener Active")

        // Setup Broadcast Receiver
        val filter = IntentFilter(CLEAR_NOTIFICATIONS)
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Filter whatsapp
        if(sbn.packageName != "com.whatsapp") return

        // Filter channel
        if(sbn.notification.channelId != "voip_notification_11") return

        // Send notification posted intent with caller name
        val intent = Intent(INCOMING_NOTIFICATION_POSTED)
        intent.putExtra(CALLER_NAME, sbn.notification.extras[Notification.EXTRA_TITLE].toString())
        intent.putExtra(ACTION_ACCEPT, sbn.notification.actions[1])
        intent.putExtra(ACTION_DECLINE, sbn.notification.actions[0])
        intent.putExtra(FULLSCREEN_INTENT, sbn.notification.fullScreenIntent)
        println("Sending notification posted broadcast")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Filter whatsapp
        if(sbn.packageName != "com.whatsapp") return

        // Filter channel
        if(sbn.notification.channelId != "voip_notification_11") return

        // Send notification removed intent
        val intent = Intent(INCOMING_NOTIFICATION_REMOVED)
        println("Sending notification removed broadcast")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    companion object {
        const val CALLER_NAME = "caller_name"
        const val ACTION_ACCEPT = "action_accept"
        const val ACTION_DECLINE = "action_decline"
        const val FULLSCREEN_INTENT = "fullscreen_intent"
        const val INCOMING_NOTIFICATION_POSTED = "incoming_posted"
        const val INCOMING_NOTIFICATION_REMOVED = "incoming_removed"
        const val CLEAR_NOTIFICATIONS = "clear_notifications"
    }
}