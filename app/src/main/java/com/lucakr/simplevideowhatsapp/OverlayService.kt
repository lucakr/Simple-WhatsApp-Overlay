package com.lucakr.simplevideowhatsapp

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
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
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ANSWER_CALL
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.DECLINE_CALL
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.END_CALL
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ERROR_NO_END_CALL_BTN
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ERROR_NO_NOTIFICATION
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.NOTIFICATION_CHANGE
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.WINDOW_CHANGE
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.WINDOW_CHANGE_NO_END_CALL
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.WINDOW_CHANGE_W_END_CALL
import com.lucakr.simplevideowhatsapp.FullscreenActivity.Companion.ACTION_MAIN_ACTIVITY_RESUMED


class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var activeOverlay: View? = null
    private lateinit var contactView:RecyclerView
    private var contactPos = 0
    private var audioManager: AudioManager ?= null

    enum class WhatsAppState {
        CLOSED, CALLING, IN_CALL, INCOMING
    }

    private var state = WhatsAppState.CLOSED

    @RequiresApi(Build.VERSION_CODES.P)
    private fun reset() {
        // Set state to closed
        state = WhatsAppState.CLOSED

        // Kill whatsapp
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.whatsapp")

        // Set default overlay
        setOverlay(R.layout.activity_fullscreen)

        // Cancel the timer just in case
        audioModeChecker.cancel()

        // Go home
        // TODO
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {

            when(intent.action) {

                /** INTENTS FROM ACCESSIBILITY SERVICE **/

                WINDOW_CHANGE_W_END_CALL -> {
                    // Audio mode tends not to change for a bit after the window change
                    Thread.sleep(AUDIO_MODE_CHANGE_DELAY.toLong())
                    val audioMode = audioManager!!.mode

                    println(audioMode.toString())

                    if(state == WhatsAppState.CLOSED && audioMode == AudioManager.MODE_IN_COMMUNICATION) {
                        state = WhatsAppState.IN_CALL
                        println("STATE: IN_CALL FROM CLOSED")
                        setOverlay(R.layout.end_overlay)
                    } else if(state == WhatsAppState.INCOMING && audioMode == AudioManager.MODE_IN_COMMUNICATION) {
                        state = WhatsAppState.IN_CALL
                        println("STATE: IN_CALL FROM INCOMING")
                        setOverlay(R.layout.end_overlay)
                        audioModeChecker.cancel()
                    } else {
                        println("ERR: Invalid state: $state for window change with end call btn")
                        //reset()
                    }
                }

                WINDOW_CHANGE_NO_END_CALL -> {
                    // Audio mode tends not to change for a bit after the window change
                    Thread.sleep(AUDIO_MODE_CHANGE_DELAY.toLong() * 2)
                    val audioMode = audioManager!!.mode

                    // Yes I know part of this is redundant. I've separate out the state changes to make it easier to understand.
                    if(state == WhatsAppState.CALLING && audioMode == AudioManager.MODE_NORMAL) {
                        println("STATE: CLOSED FROM CALLING")
                        audioModeChecker.cancel()
                        reset()
                    } else if(state == WhatsAppState.IN_CALL && audioMode == AudioManager.MODE_NORMAL) {
                        println("STATE: CLOSED FROM IN_CALL")
                        reset()
                    } else {
                        println("ERR: Invalid state: $state for window change with no end call btn")
                        //reset()
                    }
                }

                WINDOW_CHANGE -> {
                    // Audio mode tends not to change for a bit after the window change
                    Thread.sleep(AUDIO_MODE_CHANGE_DELAY.toLong())
                    val audioMode = audioManager!!.mode

                    println(audioMode.toString())

                    if(state == WhatsAppState.CLOSED && audioMode == AudioManager.MODE_IN_COMMUNICATION) {
                        println("STATE: CALLING FROM CLOSED")
                        state = WhatsAppState.CALLING
                        setOverlay(R.layout.calling_overlay)
                        audioModeChecker.start()
                    } else if(state == WhatsAppState.INCOMING && audioMode == AudioManager.MODE_IN_COMMUNICATION) {
                        println("STATE: IN_CALL FROM INCOMING")
                        state = WhatsAppState.IN_CALL
                        setOverlay(R.layout.end_overlay)
                        audioModeChecker.cancel()
                    } else if(state == WhatsAppState.CALLING && audioMode == AudioManager.MODE_NORMAL) {
                        println("STATE: CLOSED FROM CALLING")
                        audioModeChecker.cancel()
                        reset()
                    } else if(state == WhatsAppState.IN_CALL && audioMode == AudioManager.MODE_NORMAL) {
                        println("STATE: CLOSED FROM IN_CALL")
                        reset()
                    } else {
                        println("ERR: Invalid state: $state for window change")
                        //reset()
                    }
                }

                NOTIFICATION_CHANGE -> {
                    // Audio mode tends not to change for a bit after the window change
                    Thread.sleep(AUDIO_MODE_CHANGE_DELAY.toLong())
                    val audioMode = audioManager!!.mode

                    println(audioMode.toString())

                    if(state == WhatsAppState.CLOSED && audioMode == AudioManager.MODE_RINGTONE) {
                        println("STATE: INCOMING FROM CLOSED")
                        state = WhatsAppState.INCOMING
                        setOverlay(R.layout.start_overlay)
                        audioModeChecker.start()
                    } else {
                        println("ERR: Invalid state: $state for notification change")
                        //reset()
                    }
                }

                ERROR_NO_END_CALL_BTN -> {
                    println("ERR: No end call btn")
                    //reset()
                }

                ERROR_NO_NOTIFICATION -> {
                    println("ERR: No notification")
                    //reset()
                }

                /** INTENTS FROM THIS SERVICE **/

                ACTION_ACCEPT_CALL-> {
                    if(state == WhatsAppState.INCOMING) {
                        // Trigger notification accept button action
                        println("Accepting notification call")
                        sendAcceptCallViaNotification()
                    } else {
                        println("ERR: Tried to accept incoming from invalid state: $state")
                        //reset()
                    }
                }

                ACTION_DECLINE_CALL-> {
                    // Only possible in the two incoming call states, ignore if not
                    // Follow up action depends on current state
                    if(state == WhatsAppState.INCOMING) {
                        // Trigger notification decline button action
                        println("Declining notification call")
                        sendDeclineCallViaNotification()
                    } else {
                        println("ERR: Tried to decline incoming from invalid state: $state")
                        //reset()
                    }
                }

                ACTION_END_CALL-> {
                    // Only possible when in call or calling
                    if(state == WhatsAppState.IN_CALL || state == WhatsAppState.CALLING) {
                        // Trigger end call button
                        println("Ending call")
                        sendEndCall()
                    } else {
                        println("ERR: Tried to end call from invalid state: $state")
                        //reset()
                    }
                }

                /** INTENTS FROM MAIN ACTIVITY **/

                ACTION_MAIN_ACTIVITY_RESUMED-> {
                    if(state != WhatsAppState.CLOSED) {
                        println("Resumed main activity from unknown state")
                        reset()
                    }
                }
            }

        }
    }

    /** Whatsapp call times out after about 50 seconds, so we set the limit for 60s. If we reach 60s
     *  without cancelling the timer, then something is wrong and we should reset.
     */
    private val audioModeChecker = object: CountDownTimer(60000, 200) {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onFinish() {
            // Something has gone wrong
            reset()
        }

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onTick(millisUntilFinished: Long) {
            // Check the audio mode for changes
            val audioMode = audioManager!!.mode

            println("MODE: ${audioMode.toString()}")

            //if(state == WhatsAppState.CALLING && audioMode == AudioManager.MODE_IN_COMMUNICATION) {
            //    // This is a valid change condition
            //    println("STATE: IN_CALL FROM CALLING")
            //    this.cancel()
            //    state = WhatsAppState.IN_CALL
            //    setOverlay(R.layout.end_overlay)
            //} else
            if(state == WhatsAppState.INCOMING && audioMode == AudioManager.MODE_NORMAL) {
                // This is a valid change condition
                println("STATE: CLOSED FROM INCOMING")
                this.cancel()
                reset()
            } else if(state == WhatsAppState.CALLING && audioMode == AudioManager.MODE_RINGTONE) {
                // This is a valid wait condition
                return
            } else if(state == WhatsAppState.INCOMING && audioMode == AudioManager.MODE_RINGTONE) {
                // This is a valid wait condition
                return
            } else {
                // Other other conditions shouldn't happen
                //this.cancel()
                //reset()
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
        val filter = IntentFilter(ACTION_END_CALL).apply {
            addAction(ACTION_ACCEPT_CALL)
            addAction(ACTION_DECLINE_CALL)
            addAction(WINDOW_CHANGE_W_END_CALL)
            addAction(WINDOW_CHANGE_NO_END_CALL)
            addAction(WINDOW_CHANGE)
            addAction(NOTIFICATION_CHANGE)
            addAction(ERROR_NO_END_CALL_BTN)
            addAction(ERROR_NO_NOTIFICATION)
            addAction(ACTION_MAIN_ACTIVITY_RESUMED)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            LayoutParams.FLAG_NOT_FOCUSABLE,
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

                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = whatsappContacts[contactPos].myDisplayName
//                val callerImage = activeOverlay!!.findViewById<ImageView>(R.id.caller_image) as ImageView
//                if(whatsappContacts[contactPos].myThumbnail != "") {
//                    callerImage.setImageURI(whatsappContacts[contactPos].myThumbnail.toUri())
//                } else {
//                    callerImage.setImageResource(android.R.color.transparent)
//                }
            }
            R.layout.calling_overlay -> {
                activeOverlay!!.findViewById<Button>(R.id.end_button).setOnClickListener{endButtonPress(it)}

                activeOverlay!!.findViewById<TextView>(R.id.caller_name).text = whatsappContacts[contactPos].myDisplayName
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

    private fun sendAcceptCallViaNotification() {
        val intent = Intent(ANSWER_CALL)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendDeclineCallViaNotification() {
        val intent = Intent(DECLINE_CALL)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendEndCall() {
        val intent = Intent(END_CALL)
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
        val intent = Intent(ACTION_START_VIDEO)
        intent.putExtra(CALL_ID, myVideoId)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        removeOverlay()
    }

    private fun sendStartVoipCall(myVideoId: String) {
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
        private val AUDIO_MODE_CHANGE_DELAY = 500

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