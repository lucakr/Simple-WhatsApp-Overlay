package com.lucakr.simplevideowhatsapp

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.ACTION_LOCKED
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.ACTION_MAIN_ACTIVITY_RESUMED
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.ACTION_UNLOCKED
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.ACTION_ACCEPT
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.ACTION_DECLINE
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.CALLER_NAME
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.CLEAR_NOTIFICATIONS
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.FULLSCREEN_INTENT
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.INCOMING_NOTIFICATION_POSTED
import com.lucakr.simplevideowhatsapp.NotificationListener.Companion.INCOMING_NOTIFICATION_REMOVED


class AutomationService : AccessibilityService() {
    private var notification: Notification?= null
    private lateinit var endCallBtn: List<AccessibilityNodeInfoCompat>
    private lateinit var callStatus: List<AccessibilityNodeInfoCompat>
    private lateinit var callerName: String
    private var activeOverlay: View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var screenBounds = Rect()
    private var locked = false
    private var acceptAction : Notification.Action? = null
    private var declineAction: Notification.Action? = null
    private var fullscreenIntent: PendingIntent? = null

    enum class WhatsAppState {
        CLOSED, CALLING, IN_CALL, INCOMING, CLOSING
    }
    private var state = WhatsAppState.CLOSED

    private fun soundOn() {
        println("Sound on")
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING),0)
            // WhatsApp handles the STREAM_VOICE_CALL volume itself - the little punk - but I'll still do it anyway
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0)
        } catch (t: Throwable) {

        }
    }

    private fun soundOff() {
        println("Sound off")
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (t: Throwable) {

        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun closeWhatsApp() {
        state == WhatsAppState.CLOSING

        soundOff()

        // Kill whatsapp
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

        // Go home
        startActivity(
                (Intent(Intent.ACTION_MAIN)).addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
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
        when(resourceId) {
            R.layout.activity_fullscreen -> {
                activeOverlay!!.findViewById<Button>(R.id.call_button).setOnClickListener{callButtonPress(it)}
                activeOverlay!!.findViewById<Button>(R.id.left_button).setOnClickListener{scrollLeftButtonPress(it)}
                activeOverlay!!.findViewById<Button>(R.id.right_button).setOnClickListener{scrollRightButtonPress(it)}
                activeOverlay!!.findViewById<TextView>(R.id.topmost_view).setOnClickListener{middleClick(it)}

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

                var incomingCaller:String ?= null
                incomingCaller = if(callerName == "") {
                    "Unknown"
                } else {
                    callerName
                }

                val callerImage = activeOverlay!!.findViewById<ImageView>(R.id.caller_image) as ImageView

                // Try and find call in contacts
                try {
                    val foundContact = whatsappContacts.single { it.myDisplayName == incomingCaller }
                    if (foundContact.myThumbnail != "") {
                        callerImage.setImageURI(foundContact.myThumbnail.toUri())
                    } else {
                        callerImage.setImageResource(android.R.color.transparent)
                    }
                    activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = incomingCaller
                } catch (t: Throwable) {
                    callerImage.setImageResource(android.R.color.transparent)
                    activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = "Unknown"
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

                ACTION_UNLOCKED -> {
                    println("Unlocked 2")
                }

                ACTION_LOCKED -> {
                    println("Locked 2")
                    removeOverlay()
                }

            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun errorNoEndCallBtn() {
        println("No end call btn")
        state = WhatsAppState.CLOSED

        soundOff()

        // Kill whatsapp
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

        setOverlay(R.layout.activity_fullscreen)

        // Go home
        startActivity(
            (Intent(Intent.ACTION_MAIN)).addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun errorNoNotification() {
        println("No notification")
        state = WhatsAppState.CLOSED

        soundOff()

        // Kill whatsapp
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

        setOverlay(R.layout.activity_fullscreen)

        // Go home
        startActivity(
            (Intent(Intent.ACTION_MAIN)).addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate() {
        println("AUTOMATION SERVICE STARTED")

        // Setup Broadcast Receiver
        val filter = IntentFilter(ACTION_MAIN_ACTIVITY_RESUMED).apply {
            addAction(ACTION_LOCKED)
            addAction(ACTION_UNLOCKED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onServiceConnected() {
        super.onServiceConnected()
        println("AUTOMATION SERVICE CONNECTED")

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

    private fun getAllChildren(n: AccessibilityNodeInfo): ArrayList<AccessibilityNodeInfo>? {
        if (n.childCount == 0) {
            val nodeArrayList = ArrayList<AccessibilityNodeInfo>()
            nodeArrayList.add(n)
            return nodeArrayList
        }
        val result = ArrayList<AccessibilityNodeInfo>()
        val ng = n
        for (i in 0 until ng.childCount) {
            val child = ng.getChild(i)
            val nodeArrayList = ArrayList<AccessibilityNodeInfo>()
            nodeArrayList.add(n)
            try {
                nodeArrayList.addAll(getAllChildren(child)!!)
            } catch (t: Throwable) {
                return null
            }
            result.addAll(nodeArrayList)
        }
        return result
    }

    private fun area(node: AccessibilityNodeInfo): Int {
        var surfaceArea = Rect()
        node.getBoundsInScreen(surfaceArea)
        return surfaceArea.width() * surfaceArea.height()
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
            if(sourceBounds != screenBounds && event.source.viewIdResourceName != "com.whatsapp:id/call_status") return

            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(event.source)

            val tmpEndBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
            val callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")
            val tmpCallerName = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/name")
            val addParticipantBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/top_add_participant_btn")
            val callBackBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_back_btn")
            val callLabel = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/voip_call_label")
            val callRating = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_rating_title")

            if(callRating.isNotEmpty()) {
                println("Rating dialog detected")
                closeWhatsApp()
                return
            }

            if(tmpEndBtn.isNotEmpty()) {
                endCallBtn = tmpEndBtn
            }

            if(tmpCallerName.isNotEmpty()) {
                callerName = tmpCallerName[0].text.toString()
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
            } else if(callLabel.isNotEmpty() && (callLabel[0].text.contains("WHATSAPP VIDEO CALL") || callLabel[0].text.contains("WHATSAPP VOICE CALL")) && tmpEndBtn.isEmpty()) {
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
            } else if(callStatus.isNotEmpty()) {
                //println(callStatus[0].text)

                if(callStatus[0].text != null && callStatus[0].text.contains("is on another call")) {
                    endCallBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }

                when(callStatus[0].text) {
                    "Ringing", "Calling", "RINGING", "CALLING" -> {
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
                //closeWhatsApp()
            }

        } else if(event.packageName == "com.whatsapp" && event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val tmpNot = event.parcelableData as Notification
            println(event.toString())

            // Check the channel is correct
            if (tmpNot!!.group == "call_notification_group") {
                notification = tmpNot
            }
        } else if(event.packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            //println(event.toString())

            // Make sure non-null source
            if(event.source == null) return

            // Get all children of the source
            val children = getAllChildren(event.source)
            if(children.isNullOrEmpty()) return

            // First search for the app name and check its whatsapp
            val whatsappNodes = children.filter { it -> it.text == "WhatsApp" }
            if(whatsappNodes.isNullOrEmpty()) return

            //println("Verified whatsapp")

            // Check that it's an incoming call
            val incomingNodes = children.filter { it -> (it.text != null && (it.text.contains("Incoming video call", ignoreCase = false) || it.text.contains("Incoming voice call", ignoreCase = false)))}
            if(incomingNodes.isNullOrEmpty()) return

            //println("Verified incoming call")

            val clickableNodes = children.filter {it -> it.isClickable && it.viewIdResourceName == null && it.contentDescription == null && it.isVisibleToUser }
            if(clickableNodes.isEmpty()) return
            // Assume we want the largest child
            clickableNodes.sortedBy { it -> area(it) }
            while(!clickableNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)) {}
            println(clickableNodes[0].toString())

        } else {
            //println(event!!.toString())
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

    private var clickCount = 0
    private var firstTime = System.currentTimeMillis()
    private fun middleClick(view: View) {
        println("here")
        if(System.currentTimeMillis() - firstTime > 5000) {
            println("reset")
            clickCount = 0
            firstTime = System.currentTimeMillis()
        } else {
            println("click")
            clickCount++
            if(clickCount >= 10) removeOverlay()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun declineButtonPress(view: View) {
        if(notification != null) {
        //if(declineAction != null) {
            println("Declining call")
            notification!!.actions[0].actionIntent.send()
        } else {
            errorNoNotification()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun answerButtonPress(view: View) {
        if(notification != null) {
        //if(acceptAction != null) {
            println("Answering call")
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