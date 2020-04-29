package com.lucakr.simplevideowhatsapp

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_CALLING
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_CALL_ACCEPTED
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_CALL_DECLINED
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_INCOMING_FULLSCREEN_APPEAR
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_INCOMING_NOTIFICATION_APPEAR
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_INCOMING_NOTIFICATION_DISAPPEAR
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_UNANSWERED_APPEAR
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_UNDOCUMENTED_VIEW
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.ACTION_MAIN_ACTIVITY_RESUMED
import org.w3c.dom.Text


class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var activeOverlay: View? = null
    private lateinit var contactView:RecyclerView
    private var contactPos = 0

    enum class WhatsAppState {
        CLOSED, CALLING, IN_CALL, INCOMING_VIA_NOTIFICATION, INCOMING_VIA_FULLSCREEN, UNANSWERED
    }

    private var state = WhatsAppState.CLOSED

    @RequiresApi(Build.VERSION_CODES.P)
    private fun reset() {
        // Set state to closed
        state = WhatsAppState.CLOSED

        // Kill whatsapp
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

//        // Start whatsapp again
//        startActivity(packageManager.getLaunchIntentForPackage("com.whatsapp"))

        // Set default overlay
        setOverlay(R.layout.activity_fullscreen)

        // Go home
        // TODO
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {

            when(intent.action) {

                /** INTENTS FROM ACCESSIBILITY SERVICE **/

                ACTION_INCOMING_NOTIFICATION_APPEAR-> {
                    // Don't want to notify of a call if we're already in some form of call
                    if(state == WhatsAppState.CLOSED) {
                        state = WhatsAppState.INCOMING_VIA_NOTIFICATION
                        setOverlay(R.layout.start_overlay)
                    }
                }

                ACTION_INCOMING_NOTIFICATION_DISAPPEAR-> {
                    // Only care about the notification dismissal if we're depending on the notification
                    if(state == WhatsAppState.INCOMING_VIA_NOTIFICATION) {
                        state = WhatsAppState.CLOSED
                        removeOverlay()
                    }
                }

                ACTION_INCOMING_FULLSCREEN_APPEAR-> {
                    // Don't want to notify of a call if we're already in some form of call
                    if(state == WhatsAppState.CLOSED) {
                        state = WhatsAppState.INCOMING_VIA_FULLSCREEN
                        setOverlay(R.layout.start_overlay)
                    }
                }

                ACTION_UNDOCUMENTED_VIEW-> {
                    // Something else has come up, so purge and reset
                    reset()
                }

                ACTION_UNANSWERED_APPEAR-> {
                    // Should only happen from IN_CALL state, but we'll handle it for any state to be safe
                    state = WhatsAppState.UNANSWERED
                    setOverlay(R.layout.unanswered_overlay)
                }

                ACTION_CALLING-> {
                    // Should only come from closed state, something is wrong if it doesn't
                    if(state == WhatsAppState.CLOSED) {
                        println("Starting calling overlay")
                        setOverlay(R.layout.calling_overlay)

                        state = WhatsAppState.CALLING
                    } else {
                        println("Invalid current state for calling")
                        reset()
                    }
                }

                ACTION_CALL_ACCEPTED-> {
                    if(state == WhatsAppState.CALLING) {
                        println("Call accepted")
                        // Layout doesn't need to change
                        state = WhatsAppState.IN_CALL
                    } else {
                        println("Invalid current state for call accepted")
                        reset()
                    }
                }

                ACTION_CALL_DECLINED-> {
                    if(state == WhatsAppState.CALLING) {
                        println("Call accepted")

                        setOverlay(R.layout.activity_fullscreen)
                        state = WhatsAppState.CLOSED
                    } else {
                        println("Invalid current state for call declined")
                        reset()
                    }
                }

                /** INTENTS FROM THIS SERVICE **/

                ACTION_ACCEPT_CALL-> {
                    // Only possible in the two incoming call states, ignore if not
                    // Follow up action depends on current state
                    if(state == WhatsAppState.INCOMING_VIA_NOTIFICATION) {
                        // Trigger notification accept button action
                        sendAcceptCallViaNotification()

                        state = WhatsAppState.IN_CALL
                        setOverlay(R.layout.end_overlay)
                    } else if(state == WhatsAppState.INCOMING_VIA_FULLSCREEN) {
                        // Do swipe up to accept action
                        sendAcceptCallViaFullscreen()

                        state = WhatsAppState.IN_CALL
                        setOverlay(R.layout.end_overlay)
                    }
                }

                ACTION_DECLINE_CALL-> {
                    // Only possible in the two incoming call states, ignore if not
                    // Follow up action depends on current state
                    if(state == WhatsAppState.INCOMING_VIA_NOTIFICATION) {
                        // Trigger notification decline button action
                        sendDeclineCallViaNotification()

                        state = WhatsAppState.CLOSED
                        setOverlay(R.layout.activity_fullscreen)
                    } else if(state == WhatsAppState.INCOMING_VIA_FULLSCREEN) {
                        // Do swipe up to decline action
                        sendDeclineCallViaFullscreen()

                        state = WhatsAppState.CLOSED
                        setOverlay(R.layout.activity_fullscreen)
                    }
                }

                ACTION_END_CALL-> {
                    // Only possible when in call
                    if(state == WhatsAppState.IN_CALL) {
                        // Trigger end call button
                        sendEndCall()

                        state = WhatsAppState.CLOSED
                        setOverlay(R.layout.activity_fullscreen)
                    }
                }

                ACTION_ACKNOWLEDGE_UNANSWERED-> {
                    // We don't have a way to trigger the "cancel" button, so just reset
                    reset()
                }

                /** INTENTS FROM MAIN ACTIVITY **/

                ACTION_MAIN_ACTIVITY_RESUMED-> {
                    if(state == WhatsAppState.IN_CALL) {
                        // Call most likely ended by the other party
                        println("Resumed main activity after being in call")

                        state = WhatsAppState.CLOSED
                        setOverlay(R.layout.activity_fullscreen)
                    }

                    if(state != WhatsAppState.CLOSED) {
                        println("Resumed main activity from unknown state")
                        //reset()
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("OVERLAY STARTED")

        // Setup data for default overlay list
        setupDefaultList()

        // Start default overlay
        setOverlay(R.layout.activity_fullscreen)

        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        // Setup Broadcast Receiver
        val filter = IntentFilter(ACTION_INCOMING_NOTIFICATION_APPEAR).apply {
            addAction(ACTION_INCOMING_NOTIFICATION_DISAPPEAR)
            addAction(ACTION_INCOMING_FULLSCREEN_APPEAR)
            addAction(ACTION_UNDOCUMENTED_VIEW)
            addAction(ACTION_UNANSWERED_APPEAR)
            addAction(ACTION_CALLING)
            addAction(ACTION_ACCEPT_CALL)
            addAction(ACTION_DECLINE_CALL)
            addAction(ACTION_END_CALL)
            addAction(ACTION_ACKNOWLEDGE_UNANSWERED)
            addAction(ACTION_CALL_ACCEPTED)
            addAction(ACTION_CALL_DECLINED)
            addAction(ACTION_MAIN_ACTIVITY_RESUMED)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT)

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

                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = whatsappContacts[contactPos].myDisplayName
                val callerImage = activeOverlay!!.findViewById<ImageView>(R.id.caller_image) as ImageView
                if(whatsappContacts[contactPos].myThumbnail != "") {
                    callerImage.setImageURI(whatsappContacts[contactPos].myThumbnail.toUri())
                } else {
                    callerImage.setImageResource(android.R.color.transparent)
                }
            }
            R.layout.calling_overlay -> {
                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = whatsappContacts[contactPos].myDisplayName
                val callerImage = activeOverlay!!.findViewById<ImageView>(R.id.caller_image) as ImageView
                if(whatsappContacts[contactPos].myThumbnail != "") {
                    callerImage.setImageURI(whatsappContacts[contactPos].myThumbnail.toUri())
                } else {
                    callerImage.setImageResource(android.R.color.transparent)
                }
            }
            R.layout.unanswered_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.ack_unanswered_button).setOnClickListener{ackUnansweredButtonPress(it)}

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
            Log.e(OVERLAY_TAG, "Layout Inflater Service is null; can't inflate and display ${resourceId.toString()}")
        }
    }

    private fun sendAcceptCallViaFullscreen() {
        val intent = Intent(OVERLAY_NOTIFICATION_ANSWER_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendAcceptCallViaNotification() {
        val intent = Intent(OVERLAY_FULLSCREEN_ANSWER_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendDeclineCallViaFullscreen() {
        val intent = Intent(OVERLAY_NOTIFICATION_DECLINE_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendDeclineCallViaNotification() {
        val intent = Intent(OVERLAY_FULLSCREEN_DECLINE_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendEndCall() {
        val intent = Intent(OVERLAY_END_BTN_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendEndBtn() {
        val intent = Intent(ACTION_END_CALL)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendDeclineBtn() {
        val intent = Intent(ACTION_DECLINE_CALL)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendAnswerBtn() {
        val intent = Intent(ACTION_ACCEPT_CALL)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendStartVideoCall(myVideoId: String) {
        val intent2 = Intent(ACTION_CALLING)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent2)

        val intent = Intent(ACTION_START_VIDEO)
        intent.putExtra(CALL_ID, myVideoId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendStartVoipCall(myVideoId: String) {
        val intent2 = Intent(ACTION_CALLING)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent2)

        val intent = Intent(ACTION_START_VOIP)
        intent.putExtra(CALL_ID, myVideoId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun endAckUnansweredBtn() {
        val intent = Intent(ACTION_ACKNOWLEDGE_UNANSWERED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        super.onDestroy()
    }

    /** BUTTON ON CLICKS **/

    @RequiresApi(Build.VERSION_CODES.P)
    private fun declineButtonPress(view: View) {
        // Decline
        sendDeclineBtn()
    }

    private fun answerButtonPress(view: View) {
        // Answer
        sendAnswerBtn()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun endButtonPress(view: View) {
        // End
        sendEndBtn()
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

    private fun ackUnansweredButtonPress(view: View) {
        endAckUnansweredBtn()
    }

    /** WHATSAPP CONTACT LISTING **/

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
        private val OVERLAY_TAG = OverlayService::class.java.simpleName
        const val OVERLAY_END_BTN_ACTION = "overlay_end_btn"
        const val OVERLAY_NOTIFICATION_ANSWER_BTN_ACTION = "overlay_notification_answer_btn"
        const val OVERLAY_NOTIFICATION_DECLINE_BTN_ACTION = "overlay_notification_decline_btn"
        const val OVERLAY_FULLSCREEN_ANSWER_BTN_ACTION = "overlay_fullscreen_answer_btn"
        const val OVERLAY_FULLSCREEN_DECLINE_BTN_ACTION = "overlay_fullscreen_decline_btn"
        const val ACTION_ACCEPT_CALL = "accept_call"
        const val ACTION_DECLINE_CALL = "decline_call"
        const val ACTION_END_CALL = "end_call"
        const val ACTION_ACKNOWLEDGE_UNANSWERED = "acknowledge_unanswered"
        const val ACTION_START_VOIP = "start_voip"
        const val ACTION_START_VIDEO = "start_video"
        const val CALL_ID = "call_id"
    }
}