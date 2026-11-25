# A.R.I.A. Android Starter (v0.1.0)

This is a starter Android Studio project for the first A.R.I.A. prototype.

## Included
- Bengali-first screen and speech output using Android TextToSpeech.
- Push-to-talk speech recognition using Android's speech recognizer.
- Basic sample language-switch phrase handling.
- Explicit microphone permission request.
- No camera, contacts, messages, photos, or file access.

## Important limitations
- This is a prototype source project, not an installed APK.
- It does **not** implement an always-on "ARIA" wake word.
- It does **not** control other apps or send messages/calls.
- Speech recognition availability and Bengali voice quality depend on the device's installed speech services and voice data.
- Sensitive actions must be added one at a time with explicit confirmation screens.
- Android background microphone access is restricted; do not assume continuous listening will work while locked.

## Build
1. Install Android Studio on Windows.
2. Open this folder as an existing project.
3. Allow Gradle sync and install the Android SDK Platform 36 if prompted.
4. Connect the Android phone with USB debugging enabled, or use an emulator.
5. Run the `app` configuration.

If Android Studio reports a plugin/Gradle compatibility issue, update the Android Gradle Plugin and Gradle wrapper together using Android Studio's suggested compatible versions.
