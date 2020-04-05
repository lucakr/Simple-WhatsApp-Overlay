package com.lucakr.simplevideowhatsapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager


/**
 * Created by noln on 22/09/2019.
 */
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatyView: View? = null

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
        addOverlayView(type == "end")

        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun addOverlayView(isEndOverlay:Boolean) {

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

        if(isEndOverlay) {
            floatyView = inflater.inflate(R.layout.end_overlay, null)
        } else {
            floatyView = inflater.inflate(R.layout.start_overlay, null)
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
    }

    private fun sendBroadcast(success: Boolean) {
        val intent = Intent("message") //put the same message as in the filter you used in the activity when registering the receiver
        intent.putExtra("success", success)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        super.onDestroy()

        sendBroadcast(true)

    }

    @RequiresApi(Build.VERSION_CODES.P)
    public fun declineButtonPress(view: View) {
        // Kill the answer/decline overlay
        floatyView?.let {
            windowManager.removeView(it)
            floatyView = null
        }

        // Simulate swipe to decline

        // Kill this service
        onDestroy()
    }

    public fun answerButtonPress(view: View) {
        // Kill the answer/decline overlay
        floatyView?.let {
            windowManager.removeView(it)
            floatyView = null
        }

        // Simulate swipe to answer

        // Start the end overlay
        addOverlayView(true)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public fun endButtonPress(view: View) {
        // Kill the end overlay
        floatyView?.let {
            windowManager.removeView(it)
            floatyView = null
        }

        // Obtain MotionEvent object and send

        // Kill this service
        onDestroy()
    }

    companion object {
        private val OVERLAY_TAG = OverlayService::class.java.simpleName
    }
}