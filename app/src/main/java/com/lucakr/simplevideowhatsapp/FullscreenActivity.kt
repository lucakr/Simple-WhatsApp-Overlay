package com.lucakr.simplevideowhatsapp

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_CONTACTS
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lucakr.simplevideowhatsapp.OverlayService
import kotlinx.android.synthetic.main.activity_fullscreen.*


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class FullscreenActivity : AppCompatActivity() {
    private lateinit var listView:ListView
    private lateinit var selectedPerson:person
    private lateinit var svc:Intent
    private var pauseSelfInitiated = false

    fun hideStuff() {
        name_list.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        fullscreen_content_controls.visibility = View.VISIBLE
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Rehide things in case
            hideStuff()
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(bReceiver, IntentFilter("message"))

        pauseSelfInitiated = false
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(bReceiver)

        // Start protective overlay service
        launchMainService()
    }

    private fun launchMainService() {
        val svc = Intent(this, OverlayService::class.java)
        if(pauseSelfInitiated)
        {
            svc.putExtra("state","end")
        } else {
            svc.putExtra("state", "start")
        }
        pauseSelfInitiated = false

        stopService(svc)
        startService(svc)
    }

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
        }
    }

    private fun requestCallPhone()
    {
        if (ContextCompat.checkSelfPermission(this, CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(CALL_PHONE), MY_PERMISSIONS_REQUEST_CALL_PHONE)
            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
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
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    requestCallPhone();
                }
                return
            }

            MY_PERMISSIONS_REQUEST_CONTACTS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    requestContacts();
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun videoCall(id: String)
    {
        // Try each time just in case
        requestCallPhone()

        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
            Uri.parse("content://com.android.contacts/data/$id"),
            "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        )
        i.setPackage("com.whatsapp")

        // Can't get here without accepting the permission onCreate
        pauseSelfInitiated = true
        println("STARTING WHATSAPP")
        startActivity(i)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun voipCall(id: String)
    {
        // Try each time just in case
        requestCallPhone()

        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
            Uri.parse("content://com.android.contacts/data/$id"),
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        )
        i.setPackage("com.whatsapp")

        // Can't get here without accepting the permission onCreate
        pauseSelfInitiated = true
        println("STARTING WHATSAPP")
        startActivity(i)
    }

    /**
     * Start whatsapp call with selected contact
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private val mStartWhatsapp = View.OnClickListener { _ ->
        // Get contact uid
        if(::selectedPerson.isInitialized) {
            if(selectedPerson.myVideoId != "")
            {
                videoCall(selectedPerson.myVideoId)
            }
            else if(selectedPerson.myVoipId != "")
            {
                voipCall(selectedPerson.myVoipId)
            }
        }

        false
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onPostResume() {
        super.onPostResume()
        println("RETURNED FROM WHATSAPP")

        if(::svc.isInitialized)
        {
            stopService(svc)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()

        hideStuff()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fullscreen)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()

        hideStuff()

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        call_button.setOnClickListener(mStartWhatsapp)

        // Check permission for overlay
        if (!Settings.canDrawOverlays(this)) {
            // Check that the user has granted permission, and prompt them if not
            checkDrawOverlayPermission()
        }
    }

    class person(val id:Long, val displayName:String) {
        var myId:Long = id
        var myDisplayName:String = displayName
        var myThumbnail:String = ""
        var myVoipId:String = ""
        var myVideoId:String = ""
    }

    private val whatsappContacts: MutableList<person> = mutableListOf()

    private val PROJECTION: Array<out String> = arrayOf(
        ContactsContract.Data._ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.PHOTO_URI
    )

    class PersonAdapter(private val context: Context,
                        private val dataSource: MutableList<person>): BaseAdapter() {
        private val inflater: LayoutInflater
                = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        override fun getCount(): Int {
            return dataSource.size
        }

        override fun getItem(position: Int): Any {
            return dataSource[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Get view for row item
            val rowView = inflater.inflate(R.layout.list_item_recipe, parent, false)
            // Get title element
            val titleTextView = rowView.findViewById(R.id.recipe_list_title) as TextView
            // Get thumbnail element
            val thumbnailImageView = rowView.findViewById(R.id.recipe_list_thumbnail) as ImageView

            // 1
            val recipe = getItem(position) as person

            titleTextView.text = recipe.myDisplayName
            if(recipe.myThumbnail != "") {
                thumbnailImageView.setImageURI(recipe.myThumbnail.toUri())
            } else {
                thumbnailImageView.setImageResource(android.R.color.transparent)
            }

            return rowView
        }

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Request READ_CONTACTS
        requestContacts()

        // Request CALL_PHONE
        requestCallPhone()

        // Get contacts
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            PROJECTION, null, null,
            ContactsContract.Contacts.DISPLAY_NAME);

        // Parse to find valid whatsapp contacts and add to secondary array
        while(cursor!!.moveToNext()) {
            val id:Long = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data._ID))
            val displayName:String = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
            val mimeType:String =  cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))
            val thumbnail = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI))

            if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" || mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.video.call") {
                // Check if it exists in the list already
                var next: person? = whatsappContacts.find{ it.myDisplayName == displayName }

                // If not, add it in
                if(next == null) {
                    next = person(id, displayName)
                    if(thumbnail != null) next!!.myThumbnail = thumbnail
                    whatsappContacts.add(next)
                }

                // Get the index of the old or new entry
                val index = whatsappContacts.indexOf(next)

                // Update the relevant id
                if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call") {
                    next!!.myVoipId = id.toString()
                }
                else{
                    next!!.myVideoId = id.toString()
                }

                // Correct the entry
                whatsappContacts[index] = next
            }

        }

        // Populate list
        listView = findViewById<ListView>(R.id.name_list)
        val adapter = PersonAdapter(this, whatsappContacts)
        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            // This is your listview's selected item
            selectedPerson = parent.getItemAtPosition(position) as person
        }
    }


    companion object {
        private val MY_PERMISSIONS_REQUEST_CONTACTS = 0
        private val MY_PERMISSIONS_REQUEST_CALL_PHONE = 1
        private const val REQUEST_CODE = 10101
    }
}
