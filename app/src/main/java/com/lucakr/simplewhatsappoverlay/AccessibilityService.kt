package com.lucakr.simplewhatsappoverlay

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
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
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.lucakr.whatsappvideochataccessibilityservice.R

// TODO:
// 1. Call cuts out on tablet side, changes to call ended, not sure if call has actually ended.
//      Possibly the error state killing whatsapp?
// 2. Call connects but overlay doesn't change, except self camera is visible
//      Different app is still active, whatsapp has started as picture-in-picture
// 3. Multiple calls somehow?
// 4. Eventually gets deactivated


class AccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var activeOverlay: View? = null
    private var callerName: String? = null
    private var screenBounds = Rect()

    enum class OverlayState {
        HOME, IN_CALL, INCOMING, OUTGOING
    }
    private var state = OverlayState.HOME

    /** ACCESSIBILITY SERVICE FUNCTIONS **/
    override fun onCreate() {
        println("ACCESSIBILITY SERVICE STARTED")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        println("ACCESSIBILITY SERVICE CONNECTED")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        state = OverlayState.HOME
        setOverlay(state)

        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        screenBounds = Rect(0, 0, point.x, point.y)

        // TODO - check for phone and contact permissions

        // Setup Broadcast Receiver
        val filter = IntentFilter(INCOMING_POST).apply {
            addAction(INCOMING_REM)
            addAction(ACTIVE_POST)
            addAction(ACTIVE_REM)
            addAction(OUTGOING_POST)
            addAction(OUTGOING_REM)
            addAction(DECLINED_POST)
            addAction(DECLINED_REM)
            addAction(MISSED_POST)
            addAction(MISSED_REM)
            addAction(ERROR)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)

        // Setup data for default overlay list
        setupDefaultList()
    }

    override fun onInterrupt() {

    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

    }
//    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
//        /** Search WhatsApp window for changes that reflect a given call state **/
//        if (event!!.packageName == "com.whatsapp" && (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
//            // Return if its an empty source
//            if(event.source == null) return
//
//            // Return if the source isn't bound over the full screen
//            val sourceBounds = Rect()
//            event.source.getBoundsInScreen(sourceBounds)
//            if(sourceBounds != screenBounds && event.source.viewIdResourceName != "com.whatsapp:id/call_status") return
//
//            // Collect and find relevant nodes
//            val nodeInfoList = AccessibilityNodeInfoCompat.wrap(event.source)
//            val endBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/end_call_btn")
//            val callStatus = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_status")
//            val callerName = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/name")
//            val addParticipantBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/top_add_participant_btn")
//            val callBackBtn = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_back_btn")
//            val callLabel = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/voip_call_label")
//            val callRating = nodeInfoList.findAccessibilityNodeInfosByViewId("com.whatsapp:id/call_rating_title")
//
//            // Save globally needed nodes if they exist
//            if(endBtn.isNotEmpty()) savedEndCallBtn = endBtn
//            if(callerName.isNotEmpty()) savedCallerName = callerName[0].text.toString()
//
//            /** Determine the WhatsApp window based on what nodes exist **/
//            lateinit var expectedState: WhatsAppState
//
//            // Incoming call
//            if(callStatus.isNotEmpty() && callStatus[0].text != null && callStatus[0].text in arrayOf("WhatsApp video call", "WhatsApp voice call")) {
//                expectedState = WhatsAppState.INCOMING
//            }
//            // Incoming call unanswered
//            else if(callBackBtn.isNotEmpty()) {
//                expectedState = WhatsAppState.CLOSED
//            }
//            // Outgoing call
//            else if(callStatus.isNotEmpty() && callStatus[0].text != null && callStatus[0].text in arrayOf("Ringing", "Calling", "RINGING", "CALLING")) {
//                expectedState = WhatsAppState.OUTGOING
//            }
//            // Outgoing call declined
//            else if(callStatus.isNotEmpty() && callStatus[0].text == "Call declined") {
//                expectedState = WhatsAppState.CLOSED
//            }
//            // Outgoing call unanswered
//            else if(callStatus.isNotEmpty() && callStatus[0].text == "Not answered") {
//                expectedState = WhatsAppState.CLOSED
//            }
//            // Outgoing call engaged
//            else if(callStatus.isNotEmpty() && callStatus[0].text != null && callStatus[0].text.contains("is on another call")) {
//                expectedState = WhatsAppState.CLOSED
//            }
//            // Call active
//            else if(addParticipantBtn.isNotEmpty()) {
//                expectedState = WhatsAppState.IN_CALL
//            }
//            // Incoming call declined, or
//            // Incoming call ended, or
//            // Outgoing call ended
//            else if(callLabel.isEmpty()) {
//                expectedState = WhatsAppState.CLOSED
//            }
//
//            // Handle the call rating pop up on its own
//            if(callRating.isNotEmpty()) {
//                expectedState = WhatsAppState.CLOSED
//            }
//
//            // Now do things based on the state
//            var nextState: WhatsAppState? = null
//            when(expectedState) {
//                WhatsAppState.CLOSED -> {
//                    if(state != WhatsAppState.CLOSED) {
//                        setOverlay(R.layout.main_overlay)
//                        soundOff()
//                        killWhatsApp()
//                        nextState = expectedState
//                    }
//                }
//
//                WhatsAppState.IN_CALL -> {
//                    if (state == WhatsAppState.INCOMING || state == WhatsAppState.OUTGOING) {
//                        setOverlay(R.layout.end_overlay)
//                        soundOn() // Should already be on, but do this just in case
//                        nextState = expectedState
//                    }
//                }
//
//                WhatsAppState.INCOMING -> {
//                    if (state == WhatsAppState.CLOSED) {
//                        setOverlay(R.layout.start_overlay)
//                        soundOn()
//                        nextState = expectedState
//                    }
//                }
//
//                WhatsAppState.OUTGOING -> {
//                    if (state == WhatsAppState.CLOSED) {
//                        setOverlay(R.layout.calling_overlay)
//                        soundOn()
//                        nextState = expectedState
//                    }
//                }
//
//            }
//
//            // Update the current state
//            if (nextState == expectedState) {
//                state = nextState
//            } else {
//                println("Error: attempted transition to $expectedState from $state")
//            }
//
//            /** Store latest WhatsApp notification object to use for answering and declining **/
//        } else if(event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) { //event.packageName == "com.whatsapp" &&
//            val tmpNot = event.parcelableData as Notification
//            println(event.toString())
//            //println(tmpNot)
//
//            // Check the channel is correct
//            if (tmpNot.category == "call") {
//                notification = tmpNot
//            }
//
//            /** Click on WhatsApp incoming call heads up notification, triggering WhatsApp to completely open **/
//        } else if(event.packageName == "com.android.systemui" && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
//            //println(event.toString())
//
//            // Make sure non-null source
//            if(event.source == null) return
//
//            // Get all children of the source
//            val children = getAllChildren(event.source)
//            if(children.isNullOrEmpty()) return
//
//            // First search for the app name and check its whatsapp
//            val whatsappNodes = children.filter { it.text == "WhatsApp" }
//            if(whatsappNodes.isNullOrEmpty()) return
//            //println("Verified whatsapp")
//
//            // Check that it's an incoming call
//            val incomingNodes = children.filter { (it.text != null && (it.text.contains("Incoming video call", ignoreCase = false) || it.text.contains("Incoming voice call", ignoreCase = false)))}
//            if(incomingNodes.isNullOrEmpty()) return
//            //println("Verified incoming call")
//
//            val clickableNodes = children.filter { it.isClickable && it.viewIdResourceName == null && it.contentDescription == null && it.isVisibleToUser }
//            if(clickableNodes.isEmpty()) return
//
//            // Assume we want the largest child as the biggest thing to click
//            clickableNodes.sortedBy { area(it) }
//            while(!clickableNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
//                Thread.sleep(1)
//            }
//            //println(clickableNodes[0].toString())
//
//        }
//    }

    /** BROADCAST HANDLING **/
    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("RX $intent")

            // Check for error first
            if(intent.action == ERROR) {
                interfaceError()
                return
            }

            // State machine
            var newState: OverlayState? = null
            when(state) {
                OverlayState.HOME -> {
                    when(intent.action) {
                        INCOMING_POST   -> newState = OverlayState.INCOMING
                        OUTGOING_POST   -> newState = OverlayState.OUTGOING
                        else            -> println("Error: ${intent.action} received in state HOME")
                    }
                }

                OverlayState.INCOMING -> {
                    when(intent.action) {
                        INCOMING_REM    -> newState = OverlayState.HOME
                        ACTIVE_POST     -> newState = OverlayState.IN_CALL
                        else            -> println("Error: ${intent.action} received in state INCOMING")
                    }
                }

                OverlayState.IN_CALL -> {
                    when(intent.action) {
                        ACTIVE_REM      -> newState = OverlayState.HOME
                        else            -> println("Error: ${intent.action} received in state IN_CALL")
                    }
                }

                OverlayState.OUTGOING -> {
                    when(intent.action) {
                        OUTGOING_REM    -> newState = OverlayState.HOME
                        OUTGOING_POST   -> newState = OverlayState.OUTGOING // sometimes we get this
                        ACTIVE_POST     -> newState = OverlayState.IN_CALL
                        else            -> println("Error: ${intent.action} received in state OUTGOING")
                    }
                }
            }

            // Receive the extras
            callerName = intent.getStringExtra(INFO_CALLER)

            // Check if a transition actually occurred
            if(newState != null && newState != state) {
                state = newState

                // Reset the interface if we're going home
                if(state == OverlayState.HOME) {
                    soundOff()
                    interfaceReset()
                } else {
                    soundOn()
                }

                // Update Overlay
                setOverlay(state)
            }
        }
    }

    /** UTILITIES **/
    private fun soundOn() {
        println("Sound on")
        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_RING),0)
            // WhatsApp handles the STREAM_VOICE_CALL volume itself - the little punk - but I'll still do it anyway
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_VOICE_CALL), 0)
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

    private fun interfaceReset() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(RESET))
    }

    private fun interfaceError() {
        println("Interface Error")

        // Resetting trigger service
        println("Resetting Trigger Service")
        interfaceReset()

        // Kill whatsapp
        println("Killing WhatsApp")
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

        // Reset State
        println("Resetting State")
        state = OverlayState.HOME
        setOverlay(state)
    }

    private fun area(node: AccessibilityNodeInfo): Int {
        val surfaceArea = Rect()
        node.getBoundsInScreen(surfaceArea)
        return surfaceArea.width() * surfaceArea.height()
    }

    /** OVERLAY SETTINGS **/
    private fun removeOverlay() {
        // Kill active overlay
        activeOverlay?.let {
            windowManager.removeView(it)
            activeOverlay = null
        }
    }

    private fun setOverlay(currentState: OverlayState) {
        //return
        val resourceId = when(currentState) {
            OverlayState.HOME -> R.layout.main_overlay
            OverlayState.INCOMING -> R.layout.start_overlay
            OverlayState.IN_CALL -> R.layout.end_overlay
            OverlayState.OUTGOING -> R.layout.calling_overlay
        }

        // Save reference to the previous overlay while we inflate the current one
        val oldOverlay = activeOverlay

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
            R.layout.main_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.call_button).setOnClickListener{callButtonPress() }
                activeOverlay!!.findViewById<Button>(R.id.left_button).setOnClickListener{scrollLeftButtonPress() }
                activeOverlay!!.findViewById<Button>(R.id.right_button).setOnClickListener{scrollRightButtonPress() }
                activeOverlay!!.findViewById<TextView>(R.id.topmost_view).setOnClickListener{middleClick() }

                // Populate the list
                contactView = activeOverlay!!.findViewById(R.id.name_list)
                contactView.adapter = ContactAdapter(whatsappContacts)
                contactView.scrollToPosition(contactPos)

                // Suppress the layout to prevent scrolling
                contactView.suppressLayout(true)
            }

            R.layout.end_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.end_button).setOnClickListener{endButtonPress() }
                activeOverlay!!.findViewById<Button>(R.id.end_button_edge).setOnClickListener{endButtonPress() }
            }

            R.layout.start_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.decline_button).setOnClickListener{declineButtonPress() }
                activeOverlay!!.findViewById<Button>(R.id.answer_button).setOnClickListener{answerButtonPress() }

                val incomingCaller: String? = if(callerName == "") {
                    getString(R.string.unknown_caller_name)
                } else {
                    callerName
                }

                val callerImage = activeOverlay!!.findViewById(R.id.caller_image) as ImageView

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
                    activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = getString(R.string.unknown_caller_name)
                }
            }

            R.layout.calling_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.end_button).setOnClickListener{endButtonPress() }

                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = whatsappContacts[contactPos].myDisplayName
                val callerImage = activeOverlay!!.findViewById(R.id.caller_image) as ImageView
                if(whatsappContacts[contactPos].myThumbnail != "") {
                    callerImage.setImageURI(whatsappContacts[contactPos].myThumbnail.toUri())
                } else {
                    callerImage.setImageResource(android.R.color.transparent)
                }
            }
        }

        // Add the current overlay
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

        // Remove the old overlay now that the current one is up and running
        oldOverlay?.let {
            windowManager.removeView(it)
        }
    }

    /** WHATSAPP INITIATION **/

    private fun videoCall(id: String)
    {
        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
            Uri.parse("content://com.android.contacts/data/$id"),
            "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        )
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setPackage("com.whatsapp")

        // Can't get here without accepting the permission onCreate
        println("Starting WhatsApp")
        startActivity(i)
    }

    private fun voipCall(id: String)
    {
        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
            Uri.parse("content://com.android.contacts/data/$id"),
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        )
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setPackage("com.whatsapp")

        // Can't get here without accepting the permission onCreate
        println("Starting WhatsApp")
        startActivity(i)
    }

    /** BUTTON ON CLICKS **/

    private var clickCount = 0
    private var firstTime = System.currentTimeMillis()

    private fun middleClick() {
        if(System.currentTimeMillis() - firstTime > 5000) {
            clickCount = 0
            firstTime = System.currentTimeMillis()
        } else {
            clickCount++
            if(clickCount >= 10) {
                println("Manual accessibility service disable")
                removeOverlay()
                disableSelf()
            }
        }
    }

    private var btnPressTime:Long = 0

    private fun declineButtonPress() {
        if(System.currentTimeMillis() - btnPressTime < btnDebounceTime) return
        btnPressTime = System.currentTimeMillis()

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DECLINE))
    }

    private fun answerButtonPress() {
        if(System.currentTimeMillis() - btnPressTime < btnDebounceTime) return
        btnPressTime = System.currentTimeMillis()

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ANSWER))
    }

    private fun endButtonPress() {
        if(System.currentTimeMillis() - btnPressTime < btnDebounceTime) return
        btnPressTime = System.currentTimeMillis()

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(END))
    }

    private fun callButtonPress() {
        if(System.currentTimeMillis() - btnPressTime < btnDebounceTime) return
        btnPressTime = System.currentTimeMillis()

        // Get contact uid
        if(whatsappContacts[contactPos].myVideoId != "")
        {
            videoCall(whatsappContacts[contactPos].myVideoId)
        }
        else if(whatsappContacts[contactPos].myVoipId != "")
        {
            voipCall(whatsappContacts[contactPos].myVoipId)
        }
    }

    private fun scrollLeftButtonPress() {
        if(contactPos > 0) {
            contactPos--
        } else if(contactPos == 0) contactPos = whatsappContacts.size-1

        contactView.suppressLayout(false)
        contactView.scrollToPosition(contactPos)
        contactView.suppressLayout(true)
    }

    private fun scrollRightButtonPress() {
        if(contactPos < whatsappContacts.size-1) {
            contactPos++
        } else if(contactPos == whatsappContacts.size-1) contactPos = 0

        contactView.suppressLayout(false)
        contactView.scrollToPosition(contactPos)
        contactView.suppressLayout(true)
    }

    /** WHATSAPP CONTACT LISTING **/

    private lateinit var contactView: RecyclerView
    private var contactPos = 0

    class Contact(id:Long, displayName:String) {
        var myId:Long = id
        var myDisplayName:String = displayName
        var myThumbnail:String = ""
        var myVoipId:String = ""
        var myVideoId:String = ""
    }

    private val whatsappContacts: MutableList<Contact> = mutableListOf()

    private val projection: Array<out String> = arrayOf(
        ContactsContract.Data._ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.PHOTO_URI
    )

    private fun setupDefaultList() {
        // Get contacts
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection, null, null,
            ContactsContract.Contacts.DISPLAY_NAME)

        // Parse to find valid whatsapp contacts and add to secondary array
        while(cursor!!.moveToNext()) {
            val id:Long = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Data._ID))
            val displayName:String = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME))
            val mimeType:String =  cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
            val thumbnail = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_URI))

            if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" || mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.video.call") {
                // Check if it exists in the list already
                var next: Contact? = whatsappContacts.find{ it.myDisplayName == displayName }

                // If not, add it in
                if(next == null) {
                    next = Contact(id, displayName)
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

        cursor.close()

        println(whatsappContacts.toString())

    }

    class ContactAdapter(private val dataSource: MutableList<Contact>): RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        class ContactViewHolder(val contactView: LinearLayout) : RecyclerView.ViewHolder(contactView)

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
        const val INCOMING_POST = "incoming_posted"
        const val INCOMING_REM  = "incoming_removed"
        const val ACTIVE_POST   = "active_posted"
        const val ACTIVE_REM    = "active_removed"
        const val OUTGOING_POST = "outgoing_posted"
        const val OUTGOING_REM  = "outgoing_removed"
        const val DECLINED_POST = "declined_posted"
        const val DECLINED_REM  = "declined_removed"
        const val MISSED_POST   = "missed_posted"
        const val MISSED_REM    = "missed_removed"

        const val ANSWER  = "call_answer"
        const val DECLINE = "call_decline"
        const val END     = "call_end"
        const val RESET   = "interface_reset"
        const val ERROR   = "interface_error"

        const val INFO_CALLER   = "info_caller"

        const val btnDebounceTime = 2000 // 2s
    }
}
