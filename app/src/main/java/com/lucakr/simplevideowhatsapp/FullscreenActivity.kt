package com.lucakr.simplevideowhatsapp

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_CONTACTS
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_START_VIDEO
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.ACTION_START_VOIP
import com.lucakr.simplevideowhatsapp.AutomationService.Companion.CALL_ID
import kotlinx.android.synthetic.main.default_activity.*


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class FullscreenActivity : AppCompatActivity() {
    private var callPhoneGranted = false
    private var requestContactsGranted = false

    private fun sendUnlocked() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_UNLOCKED))
    }

    private fun sendLocked() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_LOCKED))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun dismissKeyguard() {
        val keyguardLock = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardLock.requestDismissKeyguard(this, null)
    }

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

                Intent.ACTION_SCREEN_ON -> {
                    println("Unlocked")
                    //sendUnlocked()
                    dismissKeyguard()
                }

                Intent.ACTION_SCREEN_OFF -> {
                    println("Locked")
                    sendLocked()
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

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == HOME_CHANGE) {
            if(!isMyLauncherDefault()) {
                unregisterReceiver(bReceiver)
                finishAndRemoveTask()
            } else {
                // Start automation service
                if (!isAccessibilityOn(this, AutomationService::class.java)) {
                    startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), ACCESS_CHANGE)
                }
            }
        }

        if(requestCode == ACCESS_CHANGE) {
            // Start automation service
            if (!isAccessibilityOn(this, AutomationService::class.java)) {
                unregisterReceiver(bReceiver)
                finishAndRemoveTask()
            }
        }

        // Check if a request code is received that matches that which we provided for the overlay draw request
        if (requestCode == REQUEST_CODE) {

            // Double-check that the user granted it, and didn't just dismiss the request
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Sorry. Can't draw overlays without permission...", Toast.LENGTH_SHORT).show()
            }
        }

        if(requestCode == MY_PERMISSIONS_REQUEST_CONTACTS) {
            requestContacts()
        }

        if(requestCode == MY_PERMISSIONS_REQUEST_CALL_PHONE) {
            requestCallPhone()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_CONTACTS -> {
                if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //requestContactsGranted = true
                    requestCallPhone()
                } else {
                    unregisterReceiver(bReceiver)
                    finishAndRemoveTask()
                }
                return
            }

            MY_PERMISSIONS_REQUEST_CALL_PHONE -> {
                if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //callPhoneGranted = true
                    if(!isMyLauncherDefault()) {
                        startActivityForResult(Intent(Settings.ACTION_HOME_SETTINGS), HOME_CHANGE)
                    } else {
                        // Start automation service
                        if (!isAccessibilityOn(this, AutomationService::class.java)) {
                            startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), ACCESS_CHANGE)
                        }
                    }
                } else {
                    unregisterReceiver(bReceiver)
                    finishAndRemoveTask()
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

    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestContacts()
    {
        if (ContextCompat.checkSelfPermission(this, READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(READ_CONTACTS), MY_PERMISSIONS_REQUEST_CONTACTS)
        } else {
            //requestContactsGranted = true
            requestCallPhone()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestCallPhone()
    {
        if (ContextCompat.checkSelfPermission(this, CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(CALL_PHONE), MY_PERMISSIONS_REQUEST_CALL_PHONE)
        } else {
            if(!isMyLauncherDefault()) {
                startActivityForResult(Intent(Settings.ACTION_HOME_SETTINGS), HOME_CHANGE)
            } else {
                // Start automation service
                if (!isAccessibilityOn(this, AutomationService::class.java)) {
                    startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), ACCESS_CHANGE)
                }
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

//        // Start automation service if it's not running
//        if (!isAccessibilityOn(this, AutomationService::class.java)) {
//            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
//            startActivity(intent)
//        } else {
//            //startService(Intent(this, AutomationService::class.java))
//        }

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
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
        registerReceiver(bReceiver, filter)

        setContentView(R.layout.default_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()

        hideNavigationAndNotification()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Start the permssion requests
        // on success - it requests call access
        // on call access request it prompts to change the home launcher
        // then prompts to enable accessibility service
        requestContacts()
    }

    private fun isMyLauncherDefault(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val info = intent.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY)
        val currentHomePackage = info!!.packageName
        return currentHomePackage == packageName
    }

    /** COMPANIONS **/

    companion object {
        private const val MY_PERMISSIONS_REQUEST_CONTACTS = 0
        private const val MY_PERMISSIONS_REQUEST_CALL_PHONE = 1
        private const val REQUEST_CODE = 10101
        private const val HOME_CHANGE = 1337
        private const val ACCESS_CHANGE = 1007
        const val ACTION_MAIN_ACTIVITY_RESUMED = "main_activity_resumed"
        const val ACTION_LOCKED = "main_activity_locked"
        const val ACTION_UNLOCKED = "main_activity_unlocked"
    }
}
