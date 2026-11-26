# ARIA Android v0.3.0

A runnable Android starter for ARIA: futuristic animated holographic-style core, Gemini chat, Bangla/English input, speech recognition, text-to-speech, and locally persisted chat history.

## Requirements
- Android Studio with JDK 17
- Android SDK Platform 35
- A Gemini API key from Google AI Studio

## Build
Open this folder in Android Studio and sync Gradle, or run:
```bash
gradle wrapper --gradle-version 8.11.1
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds a debug APK on pushes to `main` or manual workflow dispatch.

## First launch
1. Install the APK.
2. Tap the gear icon and save your Gemini API key.
3. Send a message or tap the microphone.
4. Grant microphone permission when prompted.

## Included
- Gemini 2.5 Flash REST integration (user-supplied API key).
- Bengali (Bangladesh) / English prompt behavior.
- Android speech recognition and Android system TTS (voice availability depends on installed speech services).
- Chat history stored in app-private SharedPreferences.
- Animated Canvas-rendered holographic orb and status states.
- GitHub Actions build workflow.

## Important limitations
- This is an Android MVP, not the finished all-platform JARVIS system.
- Google speech recognition and TTS depend on the Android device's installed language/voice services.
- Gemini usage limits and billing/quota are controlled by Google; no app can remove provider limits.
- API key is stored in private app storage, not a hardware-backed secret vault. Do not share the key or publish screenshots containing it.
- Windows control, wake-word always-on listening, cross-device sync, Gmail/Calendar/Drive integrations, and full 3D rendering are not implemented in this release. Windows companion requirements are documented in `windows-companion/README.md`.
- No action is silently executed by AI; device actions require separate, explicit implementation and permissions.

## Troubleshooting
- `API key not valid`: check key and enabled Gemini API access.
- `Quota exceeded`: check Google AI Studio / Cloud quota; wait or configure billing/another supported provider.
- No Bangla voice: install/update a Bangla speech recognition/TTS voice in Android settings.
- Build failure: open the full GitHub Actions log; ensure JDK 17 and SDK Platform 35 are installed.
