package com.lucakr.simplevideowhatsapp

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_CONTACTS
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.ACTION_START_VIDEO
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.ACTION_START_VOIP
import com.lucakr.simplevideowhatsapp.OverlayService.Companion.CALL_ID
import kotlinx.android.synthetic.main.activity_fullscreen.*
import kotlinx.android.synthetic.main.default_activity.*


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class FullscreenActivity : AppCompatActivity() {
    private var callPhoneGranted = false
    private var requestContactsGranted = false

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context, intent: Intent) {

            when (intent.action) {
                ACTION_START_VIDEO -> {
                    println("Starting Video call")
                    videoCall(intent.getStringExtra(CALL_ID))
                }

                ACTION_START_VOIP -> {
                    println("Starting Voip call")
                    voipCall(intent.getStringExtra(CALL_ID))
                }
            }
        }
    }

    fun hideNavigationAndNotification() {
        default_backing.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        default_backing.visibility = View.VISIBLE
    }

    /** WHATSAPP INITIATION **/

    @RequiresApi(Build.VERSION_CODES.M)
    private fun videoCall(id: String)
    {
        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
            Uri.parse("content://com.android.contacts/data/$id"),
            "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        )
        i.setPackage("com.whatsapp")

        // Can't get here without accepting the permission onCreate
        println("STARTING WHATSAPP")
        startActivity(i)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun voipCall(id: String)
    {
        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
            Uri.parse("content://com.android.contacts/data/$id"),
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        )
        i.setPackage("com.whatsapp")

        // Can't get here without accepting the permission onCreate
        println("STARTING WHATSAPP")
        startActivity(i)
    }

    /** PERMISSION SETUP **/

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkDrawOverlayPermission() {

        // Checks if app already has permission to draw overlays
        if (!Settings.canDrawOverlays(this)) {

            // If not, form up an Intent to launch the permission request
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

            // Launch Intent, with the supplied request code
            startActivityForResult(intent, REQUEST_CODE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkSettingsPermission() {

        // Checks if app already has permission to draw overlays
        if (!Settings.System.canWrite(this)) {

            // If not, form up an Intent to launch the permission request
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))

            // Launch Intent, with the supplied request code
            startActivityForResult(intent, REQUEST_CODE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Check if a request code is received that matches that which we provided for the overlay draw request
        if (requestCode == REQUEST_CODE) {

            // Double-check that the user granted it, and didn't just dismiss the request
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Sorry. Can't draw overlays without permission...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestContacts()
    {
        if (ContextCompat.checkSelfPermission(this, READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(READ_CONTACTS), MY_PERMISSIONS_REQUEST_CONTACTS)
            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else {
            callPhoneGranted = true
        }
    }

    private fun requestCallPhone()
    {
        if (ContextCompat.checkSelfPermission(this, CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(CALL_PHONE), MY_PERMISSIONS_REQUEST_CALL_PHONE)
            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else {
            requestContactsGranted = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_CALL_PHONE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do
                    callPhoneGranted = true
                }

                return
            }

            MY_PERMISSIONS_REQUEST_CONTACTS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do
                    requestContactsGranted = true
                }

                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun isAccessibilityOn(context: Context, clazz: Class<out AccessibilityService?>): Boolean {
        var accessibilityEnabled = 0
        val service = context.packageName + "/" + clazz.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (ignored: SettingNotFoundException) {
        }
        val colonSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                colonSplitter.setString(settingValue)
                while (colonSplitter.hasNext()) {
                    val accessibilityService = colonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /** CLASS OVERRIDES **/

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onPostResume() {
        super.onPostResume()
        println("RETURNED FROM WHATSAPP")

        // Let the accessibility service know that whatsapp has closed
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_MAIN_ACTIVITY_RESUMED))

        // Hide overlays
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()

        hideNavigationAndNotification()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup Broadcast Receiver
        val filter = IntentFilter(ACTION_START_VIDEO).apply {
            addAction(ACTION_START_VOIP)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)

        setContentView(R.layout.default_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()

        hideNavigationAndNotification()

        // Start the overlay service
        val overlaySvc = Intent(this, OverlayService::class.java)
        startService(overlaySvc)

        // Request READ_CONTACTS
        requestContacts()

        // Request CALL_PHONE
        requestCallPhone()

        while(!callPhoneGranted && !requestContactsGranted) {
            Thread.sleep(100)

            if(!callPhoneGranted) {
                requestCallPhone()
            }

            if(!requestContactsGranted) {
                requestContacts()
            }
        }

        // Check permission for overlay
        checkDrawOverlayPermission()

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Start automation service
        if (!isAccessibilityOn(this, AutomationService::class.java)) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }
    }

    /** COMPANIONS **/

    companion object {
        private const val MY_PERMISSIONS_REQUEST_CONTACTS = 0
        private const val MY_PERMISSIONS_REQUEST_CALL_PHONE = 1
        private const val REQUEST_CODE = 10101
        const val ACTION_MAIN_ACTIVITY_RESUMED = "main_activity_resumed"
    }
}
