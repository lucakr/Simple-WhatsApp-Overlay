# Simple WhatsApp Overlay

A simple Whatsapp enabled contact browser. Calling the contact will start a whatsapp video call via an intent. While in a call, an overlay blocks access to all Whatsapp functionality. Call can be ended by the button on the overlay. This was made for an elderly relative to video call other relatives back in Italy, so certain aspects of the app are currently hardcoded to suit that.

It currently does not prompt for permissions and will crash if they're not allowed. You'll have to manually allow permissions for:
* Phone
* Contacts
* Notification access (special)
* Accessibility service (special)

I highly suggest setting up a hardware button shortcut for enabling the accessibility service, given the entire screen is taken over by the overlay. It's also possible to disable the overlay by tapping 10 times at the top of the home screen.

### FINALLY, A BIG CAVEAT
This is my first Android Application and first time using Kotlin so don't be surprised by weird methods.

## Device Support
Testing was done on Pixel 2 and Galaxy Tab A 10.1 T515 for final use on the tablet, so I can't guarantee support for other devices. If you want support, ask nicely or fork and try it yourself.

## Installation
Clone this repository and import into **Android Studio**
```bash
git clone git@github.com:lucakr/Simple-WhatsApp-Overlay.git
```