# Fevi AI Clipper for Android

**Fevi AI Clipper** is a native Android application engineered with Kotlin, Jetpack Compose, and Material Design 3. It automatically turns long-form videos into high-engagement short-form videos with AI-assisted speech recognition, smart duration clipping, dynamic subtitles, and YouTube-ready metadata.

Optimized specifically for mid-range Android devices, including the **Motorola Moto G57 Power** running **Android 17**.

---

## 🌟 Key Features

1. **AI Automatic Clipper**
   - 12-step clipping pipeline analyzing audio energy, speech pauses (VAD), and topic shifts.
   - Intelligent sentence boundary snapping to prevent cutting off speech mid-word.
   - Non-maximum suppression deduplication to prevent repetitive clips.
   - Content style targeting: Facts, Educational, Podcast, Storytelling, Motivational, Comedy, Gaming, and General.
   - Transparent selection reasons (e.g., *"Strong opening statement & hook"*, *"Complete sentence ending at 30s"*).

2. **Custom Length Maker**
   - Quick duration presets: **15s, 30s, 45s, 60s, 90s, 2 min, 3 min**, or custom user input.
   - **Smart AI Mode**: Finds complete, self-contained thoughts that fit the requested duration.
   - **Sequential Mode**: Chronologically splits the source video into continuous sequential segments.
   - Output aspect ratio formatting: **9:16 Vertical Shorts/Reels**, **1:1 Square**, **16:9 Landscape**, and **4:5 Portrait**.

3. **Automatic AI Subtitles & Captions**
   - Transcribes spoken audio into timestamped dialogue.
   - Dynamic word-by-word highlighting in real-time video playback.
   - Curated caption presets:
     - **Bold Yellow** (viral shorts caption style)
     - **Clean White** (crisp text with solid black outline)
     - **Neon Cyan Box** (high-contrast modern pill)
     - **Minimal** (subtle shadow)
     - **Large Contrast** (maximum mobile readability)
   - Export standard **SRT** and **WebVTT** caption files.
   - Burn subtitles permanently into exported videos or keep companion subtitle tracks.

4. **AI Metadata Generator (YouTube Shorts Ready)**
   - Powered by **Gemini 3.5 Flash** (via direct REST API with 60-second timeouts) or an intelligent offline template generator grounded in the transcript.
   - Generates:
     - Viral YouTube Shorts titles (under 60 characters with hook gaps)
     - Full SEO descriptions with timestamps and calls to action
     - Social media captions for TikTok and Instagram Reels
     - High-CTR hashtags and search keywords
     - Suggested thumbnail text
     - Pinned comment to drive engagement
     - Concise clip summary
   - 1-click copy buttons for individual fields and 1-click copy-all.

5. **Local Privacy & Hardware-Accelerated Performance**
   - Native Android `MediaExtractor` and `MediaMuxer` lossless video trimming for instant exports without re-encoding quality loss.
   - Saves exported clips directly to device MediaStore under `Movies/FeviClipper` for immediate access in Google Photos, Gallery, and YouTube app.
   - Zero-upload architecture: Source videos never leave the user's device.
   - Built-in storage cache management with one-tap temporary file cleanup.

---

## 📱 Architecture & Tech Stack

- **UI:** Jetpack Compose, Material 3, Edge-to-Edge window insets.
- **Language:** Kotlin 2.2.10.
- **Media Engine:** AndroidX Media3 (ExoPlayer, Transformer), Android Hardware MediaExtractor & MediaMuxer.
- **Database:** Room with KSP (`FeviDatabase`).
- **Networking & AI:** Retrofit, OkHttp, Gemini 3.5 Flash REST API.
- **Preferences:** Jetpack DataStore.
- **Testing:** JUnit, Robolectric, local JVM unit tests.

---



## 🧠 Offline AI models (no API key required)

Fevi can run its text intelligence and English speech recognition locally after the models are downloaded:

- **Qwen2.5-0.5B-Instruct Q4_K_M** (about 400 MB): open **Settings & Privacy → Offline AI models → Download Qwen model**. Qwen is used to rank transcript-based clip candidates and generate titles, descriptions, hashtags, keywords, summaries, thumbnail text and pinned comments.
- **Whisper Tiny English INT8** (about 105 MB): downloaded automatically the first time you run transcription. It recognizes the video's actual audio locally and creates timestamped transcript sections for captions and clip selection.
- The models are stored in app-private storage, not bundled into the APK. Allow enough free storage and use a stable connection for the first download. Once downloaded, model inference can run offline.
- The English Whisper model is currently English-only. The first run may take longer, and performance depends on the phone's CPU and available memory. Qwen inference uses a small context and CPU threads to reduce memory pressure.
- If Qwen is not downloaded or inference fails, the app falls back to transcript-based local metadata. It does not fabricate dialogue as a substitute for speech recognition.

The app's source video and transcript are processed on the device by these local engines. Model downloads require internet access only for the initial download.

## 🚀 Building & Downloading APK via GitHub Actions

This repository includes a production-grade GitHub Actions workflow configured at `.github/workflows/android-apk.yml`.

### How to Download the Built APK:

1. Push your changes to the repository or trigger the workflow manually from the **Actions** tab on GitHub.
2. Under **All workflows**, select the latest **Android APK Build** run.
3. Once the build finishes successfully (marked with a green checkmark):
   - Scroll down to the **Artifacts** section at the bottom of the workflow summary page.
   - Click on **`fevi-ai-clipper-debug-apk`** to download the ZIP file containing the debug `.apk`.
   - Extract the `.apk` and install it directly on your Android device (or via ADB: `adb install -r app-debug.apk`).

---

## 🛠️ Local Development & Testing

Run unit tests locally:
```bash
./gradlew testDebugUnitTest
```

Build the debug APK locally:
```bash
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
