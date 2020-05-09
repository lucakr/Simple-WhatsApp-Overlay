package com.lucakr.simplevideowhatsapp

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.provider.ContactsContract
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.ACTION_MAIN_ACTIVITY_RESUMED


class AutomationService : AccessibilityService() {
    private var notification: Notification?= null
    private lateinit var endCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var callStatus: List<AccessibilityNodeInfoCompat>
    private lateinit var callerName: List<AccessibilityNodeInfoCompat>
    private var activeOverlay: View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var screenBounds = Rect()

    enum class WhatsAppState {
        CLOSED, CALLING, IN_CALL, INCOMING, CLOSING
    }
    private var state = WhatsAppState.CLOSED

    private fun soundOn() {
        println("Sound on")
        audioManager.setStreamVolume(AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
        // WhatsApp handles the STREAM_VOICE_CALL volume itself - the little punk - but I'll still do it anyway
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0)
    }

    private fun soundOff() {
        println("Sound off")
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun closeWhatsApp() {
        state == WhatsAppState.CLOSING

        soundOff()

        // Kill whatsapp
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

        // Go home
        startActivity((Intent(Intent.ACTION_MAIN)).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun removeOverlay() {
        // Kill active overlay
        activeOverlay?.let {
            windowManager.removeView(it)
            activeOverlay = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.P)
    private fun setOverlay(resourceId: Int) {
        // Remove the previous overlay first
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        )

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        activeOverlay = inflater.inflate(resourceId, null)

        // Custom listeners
        activeOverlay!!.findViewById<TextView>(R.id.topmost_view).setOnTouchListener { v, event ->  true}
        when(resourceId) {
            R.layout.activity_fullscreen -> {
                activeOverlay!!.findViewById<Button>(R.id.call_button).setOnClickListener{callButtonPress(it)}
                activeOverlay!!.findViewById<Button>(R.id.left_button).setOnClickListener{scrollLeftButtonPress(it)}
                activeOverlay!!.findViewById<Button>(R.id.right_button).setOnClickListener{scrollRightButtonPress(it)}

                // Populate the list
                contactView = activeOverlay!!.findViewById<RecyclerView>(R.id.name_list)
                contactView.adapter = ContactAdapter(whatsappContacts)
                contactView.scrollToPosition(contactPos)

                // Suppress the layout to prevent scrolling
                contactView.suppressLayout(true)
            }
            R.layout.end_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.end_button).setOnClickListener{endButtonPress(it)}
                activeOverlay!!.findViewById<Button>(R.id.end_button_edge).setOnClickListener{endButtonPress(it)}
            }
            R.layout.start_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.decline_button).setOnClickListener{declineButtonPress(it)}
                activeOverlay!!.findViewById<Button>(R.id.answer_button).setOnClickListener{answerButtonPress(it)}

                var incomingCaller:CharSequence ?= null
                incomingCaller = if(callerName.isEmpty()) {
                    "Unknown"
                } else {
                    callerName[0].text
                }

                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = incomingCaller

                // Try and find call in contacts
                val foundContact = whatsappContacts.single { it.myDisplayName == incomingCaller }

                val callerImage = activeOverlay!!.findViewById<ImageView>(R.id.caller_image) as ImageView
                if(foundContact == null) {
                    callerImage.setImageResource(android.R.color.transparent)
                } else {
                    if (foundContact.myThumbnail != "") {
                        callerImage.setImageURI(foundContact.myThumbnail.toUri())
                    } else {
                        callerImage.setImageResource(android.R.color.transparent)
                    }
                }
            }
            R.layout.calling_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.end_button).setOnClickListener{endButtonPress(it)}

                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = whatsappContacts[contactPos].myDisplayName
                val callerImage = activeOverlay!!.findViewById<ImageView>(R.id.caller_image) as ImageView
                if(whatsappContacts[contactPos].myThumbnail != "") {
                    callerImage.setImageURI(whatsappContacts[contactPos].myThumbnail.toUri())
                } else {
                    callerImage.setImageResource(android.R.color.transparent)
                }
            }
        }

        activeOverlay?.let {
            it.systemUiVisibility  =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            windowManager.addView(activeOverlay, params)
        } ?: run {
        }
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {
            // Check the intent is for us
            when(intent.action) {
                ACTION_MAIN_ACTIVITY_RESUMED -> {
                    state = WhatsAppState.CLOSED

                    soundOff()

                    // Kill whatsapp
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    am.killBackgroundProcesses("com.whatsapp")

                    setOverlay(R.layout.activity_fullscreen)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun errorNoEndCallBtn() {
        println("No end call btn")
        closeWhatsApp()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun errorNoNotification() {
        println("No notification")
        closeWhatsApp()
    }

    override fun onCreate() {
        println("AUTOMATION SERVICE STARTED")

        // Setup Broadcast Receiver
        val filter = IntentFilter(ACTION_MAIN_ACTIVITY_RESUMED)
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onServiceConnected() {
        super.onServiceConnected()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        //setOverlay(R.layout.activity_fullscreen)

        var point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        screenBounds = Rect(0, 0, point.x, point.y)

        // Setup data for default overlay list
        setupDefaultList()
    }

    override fun onInterrupt() {

    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event!!.packageName == "com.whatsapp" && (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            //println(event.toString())
            if(event.source != null) {
                //println(event.source.toString())
            } else {
                return
            }

            // Return if the source isn't bound over the full screen
            var sourceBounds = Rect()

            event.source.getBoundsInScreen(sourceBounds)
            if(sourceBounds != screenBounds) return

            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(event.source)

            val tmpEndBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
            val callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")
            val tmpCallerName = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/name")
            val addParticipantBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/top_add_participant_btn")
            val callBackBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_back_btn")

            if(tmpEndBtn.isNotEmpty()) {
                endCallBtn = tmpEndBtn
            }

            if(tmpCallerName.isNotEmpty()) {
                callerName = tmpCallerName
            }

            if(callBackBtn.isNotEmpty()) {
                println("CLOSING from $state during unanswered transition")
                closeWhatsApp()
            } else if(addParticipantBtn.isNotEmpty()) {
                if (state == WhatsAppState.IN_CALL) return

                if (state == WhatsAppState.CALLING || state == WhatsAppState.INCOMING) {
                    println("IN_CALL from CALLING or INCOMING")
                    state = WhatsAppState.IN_CALL
                    setOverlay(R.layout.end_overlay)
                    soundOn() // Should already be on, but do this just in case
                } else {
                    println("CLOSING from $state during IN_CALL transition")
                    closeWhatsApp()
                }
            } else if(callStatus.isNotEmpty()) {
                when(callStatus[0].text) {
                    "Ringing", "Calling" -> {
                        if(state == WhatsAppState.CALLING) return

                        if(state == WhatsAppState.CLOSED) {
                            println("CALLING from CLOSED")
                            state = WhatsAppState.CALLING
                            setOverlay(R.layout.calling_overlay)
                            soundOn()
                        } else {
                            println("CLOSING from $state during CALLING transition")
                            closeWhatsApp()
                        }
                    }
                    "Call declined", "Not answered" -> {
                        if(state == WhatsAppState.CLOSING) return

                        if(state == WhatsAppState.CALLING) {
                            println("CLOSING from CALLING")
                            closeWhatsApp()
                        } else {
                            println("CLOSING from $state during CLOSING transition")
                            closeWhatsApp()
                        }
                    }
                    "WhatsApp video call", "WhatsApp voice call" -> {
                        if(state == WhatsAppState.INCOMING) return

                        if(state == WhatsAppState.CLOSED) {
                            println("INCOMING from CLOSED")
                            state = WhatsAppState.INCOMING
                            setOverlay(R.layout.start_overlay)
                            soundOn()
                        } else {
                            println("CLOSING from $state during INCOMING transition")
                            closeWhatsApp()
                        }
                    }
                }
            } else {
                if(tmpEndBtn.isNotEmpty()) return // quick fix for early exit on call answer
                println("CLOSING from $state during unknown transition")
                closeWhatsApp()
            }

        } else if(event.packageName == "com.whatsapp" && event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val tmpNot = event.parcelableData as Notification

            // Check the channel is correct
            if (tmpNot!!.channelId == "voip_notification_11") {
                notification = tmpNot
            }
        } else if(event.packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            //println(event)

            // Make sure non-null source
            if(event.source == null) return

            // First search for the app name and check its whatsapp
            var appName: AccessibilityNodeInfo ?= null
            try {
                appName = event.source.getChild(0).getChild(1).getChild(1).getChild(3).getChild(0).getChild(0).getChild(1)
            } catch (t: Throwable) {
                //println(t.toString())
                return
            }
            if(appName == null) return
            if(appName.text != "WhatsApp") return

//            println("Verified whatsapp")

            // Check that it's an incoming call
            var subTitle: AccessibilityNodeInfo ?= null
            try {
                subTitle = event.source.getChild(0).getChild(1).getChild(1).getChild(3).getChild(0).getChild(1).getChild(0).getChild(1)
            } catch (t: Throwable) {
                //println(t.toString())
                return
            }
            if(subTitle == null) return
            if(subTitle.text == null) return
            if(!subTitle.text.contains("Incoming video call", ignoreCase = false) && !subTitle.text.contains("Incoming voice call", ignoreCase = false)) return

//            println("Verified incoming call")

            // Get the child of the main view and click it
            try {
                //notification!!.fullScreenIntent.send()
                val mainView = event.source.getChild(0).getChild(1).getChild(1)
                mainView.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                println("Notification clicked")
            } finally {
                return
            }

        } else {
//            println(event!!.toString())
        }

    }

    /** BROADCAST EVENTS **/

    private fun sendStartVideoCall(myVideoId: String) {
        val intent = Intent(ACTION_START_VIDEO)
        intent.putExtra(CALL_ID, myVideoId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendStartVoipCall(myVideoId: String) {
        val intent = Intent(ACTION_START_VOIP)
        intent.putExtra(CALL_ID, myVideoId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /** BUTTON ON CLICKS **/

    @RequiresApi(Build.VERSION_CODES.P)
    private fun declineButtonPress(view: View) {
        if(notification != null) {
            println("Declining call")
            notification!!.actions[0].actionIntent.send()
        } else {
            errorNoNotification()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun answerButtonPress(view: View) {
        if(notification != null) {
            println("Answering call")
            //println(notification!!.toString())
            notification!!.actions[1].actionIntent.send()
        } else {
            errorNoNotification()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun endButtonPress(view: View) {
        // Check endCallBtn to be sure
        if (endCallBtn.isNotEmpty()) {
            println("Performing end call action")

            // Click to end call
            endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            errorNoEndCallBtn()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun callButtonPress(view: View) {
        // Get contact uid
        if(whatsappContacts[contactPos].myVideoId != "")
        {
            sendStartVideoCall(whatsappContacts[contactPos].myVideoId)
        }
        else if(whatsappContacts[contactPos].myVoipId != "")
        {
            sendStartVoipCall(whatsappContacts[contactPos].myVoipId)
        }

        false
    }

    private fun scrollLeftButtonPress(view: View) {
        if(contactPos > 0) contactPos--
        contactView.suppressLayout(false)
        contactView.scrollToPosition(contactPos)
        contactView.suppressLayout(true)

        false
    }

    private fun scrollRightButtonPress(view: View) {
        if(contactPos < whatsappContacts.size-1) contactPos++
        contactView.suppressLayout(false)
        contactView.scrollToPosition(contactPos)
        contactView.suppressLayout(true)

        false
    }

    /** WHATSAPP CONTACT LISTING **/

    private lateinit var contactView:RecyclerView
    private var contactPos = 0

    class contact(val id:Long, val displayName:String) {
        var myId:Long = id
        var myDisplayName:String = displayName
        var myThumbnail:String = ""
        var myVoipId:String = ""
        var myVideoId:String = ""
    }

    private val whatsappContacts: MutableList<contact> = mutableListOf()

    private val PROJECTION: Array<out String> = arrayOf(
        ContactsContract.Data._ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.PHOTO_URI
    )

    private fun setupDefaultList() {
        // Get contacts
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            PROJECTION, null, null,
            ContactsContract.Contacts.DISPLAY_NAME)

        // Parse to find valid whatsapp contacts and add to secondary array
        while(cursor!!.moveToNext()) {
            val id:Long = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID))
            val displayName:String = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
            val mimeType:String =  cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))
            val thumbnail = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI))

            if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" || mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.video.call") {
                // Check if it exists in the list already
                var next: contact? = whatsappContacts.find{ it.myDisplayName == displayName }

                // If not, add it in
                if(next == null) {
                    next = contact(id, displayName)
                    if(thumbnail != null) next.myThumbnail = thumbnail
                    whatsappContacts.add(next)
                }

                // Get the index of the old or new entry
                val index = whatsappContacts.indexOf(next)

                // Update the relevant id
                if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call") {
                    next.myVoipId = id.toString()
                }
                else{
                    next.myVideoId = id.toString()
                }

                // Correct the entry
                whatsappContacts[index] = next
            }

        }

        println(whatsappContacts.toString())

    }

    class ContactAdapter(private val dataSource: MutableList<contact>): RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        class ContactViewHolder(contactView: LinearLayout) : RecyclerView.ViewHolder(contactView) {
            val contactView: LinearLayout = contactView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val contactView = LayoutInflater.from(parent.context).inflate(R.layout.contact, parent, false) as LinearLayout

            return ContactViewHolder(contactView)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val titleTextView = holder.contactView.findViewById(R.id.contact_title) as TextView
            val thumbnailImageView = holder.contactView.findViewById(R.id.contact_thumbnail) as ImageView

            val curContact = dataSource[position]

            titleTextView.text = curContact.myDisplayName
            if(curContact.myThumbnail != "") {
                thumbnailImageView.setImageURI(curContact.myThumbnail.toUri())
            } else {
                thumbnailImageView.setImageResource(android.R.color.transparent)
            }
        }

        override fun getItemCount() = dataSource.size

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
        const val ACTION_START_VOIP = "start_voip"
        const val ACTION_START_VIDEO = "start_video"
        const val CALL_ID = "call_id"
    }
}