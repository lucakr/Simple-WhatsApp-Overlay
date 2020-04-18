package com.lucakr.simplevideowhatsapp

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatyView: View? = null
    private var overlayActive: Boolean = false

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {
            // Check the intent is for us
            if(overlayActive) {
                overlayActive = false

                println("Got broadcast in overlay")
                if (intent.action == OVERLAY_DESTROY_ACTION) {
                    // Kill the answer/decline overlay
                    floatyView?.let {
                        windowManager.removeView(it)
                        floatyView = null
                    }

                    sendDestroyed()
                } else if (intent.action == OVERLAY_POST_ACCEPT_CALL) {
                    // Start the end overlay
                    addOverlayView("end")
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type:String? = intent!!.getStringExtra("state")
        if(type.isNullOrBlank())
        {
            onDestroy()
        }
        addOverlayView(type!!)

        println("OVERLAY STARTED")

        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        // Setup Broadcast Receiver
        val filter = IntentFilter(OVERLAY_DESTROY_ACTION).apply {
            addAction(OVERLAY_POST_ACCEPT_CALL)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun addOverlayView(overlayType: String) {

        val params: LayoutParams
        val layoutParamsType: Int = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            LayoutParams.TYPE_PHONE
        }

        params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            layoutParamsType,
            0,
            PixelFormat.TRANSLUCENT)

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        if(overlayType == "end") {
            floatyView = inflater.inflate(R.layout.end_overlay, null)
        } else if (overlayType == "start") {
            floatyView = inflater.inflate(R.layout.start_overlay, null)
        } else if (overlayType == "protect") {
            floatyView = inflater.inflate(R.layout.protect_overlay, null)
        } else {
            println("Invalid overlay")
            return
        }

        floatyView?.let {
            it.systemUiVisibility  =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            windowManager.addView(floatyView, params)
        } ?: run {
            Log.e(OVERLAY_TAG, "Layout Inflater Service is null; can't inflate and display R.layout.floating_view")
        }

        overlayActive = true;
    }

    private fun sendDestroyed() {
        val intent = Intent(OVERLAY_POST_DESTROY)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendEndBtn() {
        val intent = Intent(OVERLAY_END_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendDeclineBtn() {
        val intent = Intent(OVERLAY_DECLINE_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendAnswerBtn() {
        val intent = Intent(OVERLAY_ANSWER_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun declineButtonPress(view: View) {
        // Kill the current overlay
        floatyView?.let {
            windowManager.removeView(it)
            floatyView = null
        }

        // Decline
        sendDeclineBtn()
    }

    fun answerButtonPress(view: View) {
        // Kill the current overlay
        floatyView?.let {
            windowManager.removeView(it)
            floatyView = null
        }

        // Answer
        sendAnswerBtn()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun endButtonPress(view: View) {
        // Kill the current overlay
        floatyView?.let {
            windowManager.removeView(it)
            floatyView = null
        }

        // End
        sendEndBtn()
    }

    companion object {
        private val OVERLAY_TAG = OverlayService::class.java.simpleName
        const val OVERLAY_END_BTN_ACTION = "overlay_end_btn"
        const val OVERLAY_ANSWER_BTN_ACTION = "overlay_answer_btn"
        const val OVERLAY_DECLINE_BTN_ACTION = "overlay_decline_btn"
        const val OVERLAY_DESTROY_ACTION = "overlay_destroy"
        const val OVERLAY_POST_DESTROY = "overlay_destroy_done"
        const val OVERLAY_POST_ACCEPT_CALL = "overlay_accept_done"
    }
}