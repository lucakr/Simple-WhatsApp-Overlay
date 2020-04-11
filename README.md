# Simple Video Whatsapp Application

A simple Whatsapp enabled contact browser. Calling the contact will start a whatsapp video call via an intent. While in a call, an overlay blocks access to all Whatsapp functionality. Call can be ended by the button on the overlay.

The app uses phone access, an accessibility service, and a system alert window to automate the whatsapp call handling and prevent the user from pressing any unwanted buttons. Ideally this is used with a Kiosk app such as SureLock to limit the tablet to just this application.

This was made for an elderly relative to video call other relatives back in Italy, so certain aspects of the app are currently hardcoded to suit that.

### FINALLY, A BIG CAVEAT
This is my first Android Application and first time using Kotlin so don't be surprised by weird methods.

## Device Support
Testing was done on Pixel 2 and Galaxy Tab A 10.1 T515 for final use on the tablet, so I can't guarantee support for other devices. If you want support, ask nicely or fork and try it yourself.

## Installation
Clone this repository and import into **Android Studio**
```bash
git clone git@github.com:lucakr/SimpleVideoWhatsapp.git
```