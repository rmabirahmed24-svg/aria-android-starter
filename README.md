# ARIA Android v0.5.0 — Auto Brain + Wake Word

A practical Android personal-assistant starter preserving the dark cyan holographic interface and adding a two-provider AI fallback, Google Search grounding, voice input/output, local chat memory and an opt-in foreground wake listener.

## Requirements
- Android Studio, JDK 17, Android SDK 35
- Gemini API key from Google AI Studio
- Optional OpenAI API key for backup brain

## Build
Open the project folder in Android Studio and sync Gradle. Or run `gradle assembleDebug` with Gradle 8.11.1 and SDK Platform 35 installed. GitHub Actions builds the debug APK on push to `main` or manual workflow dispatch.

## Setup
1. Install the APK and open ARIA.
2. Open Settings and add Gemini API key. Optionally add an OpenAI API key.
3. AUTO tries Gemini 3.8 Flash first, then OpenAI `gpt-4.1-mini` if Gemini fails.
4. Enable “Always listen for ARIA” and grant microphone permission. Android may also ask for notification permission.
5. A persistent notification shows that the microphone wake listener is active. Tap Stop listening in that notification to turn it off.

## Included in this release
- Futuristic animated cyan holographic orb (Canvas; not a 3D model).
- Gemini Interactions API with Google Search tool.
- OpenAI Responses API as a fallback provider.
- Bengali (Bangladesh) / English prompt behavior.
- Android speech recognition and Android system TextToSpeech (voice availability depends on installed speech services).
- Local app-private chat history (last 30 messages).
- User-enabled foreground wake listener for “ARIA”, “আরিয়া”, “আড়িয়া”, or “Wake up”.
- Small explicit browser shortcuts for YouTube, Google, Gmail and Maps.

## Important limitations and privacy
- This is an Android MVP, not a full ChatGPT Premium clone or an unrestricted JARVIS. Provider API keys, quotas, billing and model availability are controlled by their providers. OpenAI API billing is separate from ChatGPT subscriptions.
- AUTO means Gemini primary and OpenAI fallback; both API keys must be supplied for failover. If Gemini works, OpenAI is not called.
- The wake listener uses the phone's installed SpeechRecognizer provider, not a dedicated low-power on-device wake-word engine. It may stop/restart, may need network/provider support despite offline preference, and battery/OEM restrictions can affect reliability. After detecting the phrase, ARIA posts a notification; tap it to start the conversation. It does not silently open the app over other apps.
- Android 14+ microphone foreground services are subject to while-in-use permission and background-start restrictions. Start wake listening from the visible app after granting microphone permission. The persistent notification is intentional and required for transparency.
- The Android speech provider may capture audio and process it according to that provider's privacy policy. Wake listening is off until the user enables it; stop it from the persistent notification or app Settings.
- API keys are stored in app-private SharedPreferences in this prototype. This is not a production-grade secret vault. For public distribution, move provider keys to a secure backend and never commit keys to GitHub.
- Google Search grounding is enabled for Gemini requests. OpenAI fallback does not have web search enabled in this starter.
- No unrestricted phone control, background screen reading, auto-send messages, Gmail/Calendar account access, cloud sync, or Windows control is implemented. Sensitive device actions must be implemented separately with Android APIs, user permissions and confirmations.
- Windows companion is a future separate application; its folder contains planning notes only.

## Troubleshooting
- `API key not valid`: verify the key and provider API access.
- `Quota exceeded`: check provider usage/quota; there is no unlimited mode.
- Wake word does not trigger: check microphone and notification permissions, keep the persistent notification enabled, update Google speech services, and disable battery restriction for testing if the phone manufacturer allows it.
- No Bangla voice: install a Bangla speech recognition/TTS voice in Android settings.
- Build failure: use JDK 17, Gradle 8.11.1 and Android SDK 35; inspect the full GitHub Actions log.
