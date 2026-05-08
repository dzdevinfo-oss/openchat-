# OpenChat — Production-Grade Android AI IDE

OpenChat is a high-performance, native Android application built with Jetpack Compose, designed to provide a pixel-perfect AI interaction experience with full workspace and agentic capabilities.

## 🚀 Features

- **Blazing Fast Streaming:** Tokens appear instantly with minimal latency.
- **Agentic Workspace:** AI can read, create, and edit files in your local workspace.
- **Multimodal Support:** Attach images and documents for rich context.
- **Artifacts System:** Render HTML/JS/CSS snippets in a live preview.
- **Local Persistence:** Full session history and workspace files stored via Room and internal storage.
- **Voice Integration:** Speech-to-Text and Text-to-Speech support for hands-free interaction.
- **Pro Design:** Minimal, dark/light theme (inspired by Claude Android).

## 🛠 Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture + Repository Pattern
- **Dependency Injection:** Dagger Hilt
- **Database:** Room (SQLite)
- **Networking:** Retrofit + OkHttp (SSE support)
- **Local Storage:** DataStore (Preferences)
- **Formatting:** Markwon (Markdown) + Prism.js (Syntax Highlighting)

## 📦 Build & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/openchat.git
cd openchat
```

### 2. Setup API Keys
OpenChat supports multi-provider integration. You don't need to hardcode keys:
1. Open the app on your device.
2. Navigate to **History (Sidebar) > API Config**.
3. Add your OpenAI, Anthropic, or custom provider keys.

### 3. Build the APK locally
Ensure you have Android Studio Hedgehog+ and JDK 17 installed.
```bash
./gradlew assembleDebug
```
The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### 4. GitHub Actions (Auto-Build & Release)
The repository is configured with GitHub Actions to automate builds:

- **Debug Builds:** Every push to `main` generates a debug APK in the "Actions" tab.
- **Release Builds:** Pushing a tag (e.g., `v1.0.0`) triggers a signed release APK and a GitHub Release.

### 6. Pushing Changes from Termux (Mobile)

If you are using Termux to push these changes to GitHub, follow these exact steps to ensure the build fixes are applied:

1. **Navigate to the project folder:**
   ```bash
   cd /path/to/your/project
   ```

2. **Fix the local Gradle wrapper and Permissions (IMPORTANT):**
   Run these commands to ensure the correct files are present and executable:
   ```bash
   mkdir -p gradle/wrapper
   curl -L https://github.com/gradle/gradle/raw/v8.6.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
   chmod +x gradlew
   # Tell Git to track the executable bit (CRITICAL for GitHub Actions)
   git update-index --chmod=+x gradlew
   ```

3. **Stage and Push:**
   ```bash
   git add .
   git add -f gradle/wrapper/gradle-wrapper.jar
   git add -f app/src/main/res/drawable/ic_launcher_background.xml
   git add -f app/src/main/res/drawable/ic_launcher_foreground.xml
   git add -f app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
   git add -f app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
   git commit -m "Fix: Add missing icons and ensure gradlew executable bit"
   git push origin main
   ```

Once pushed, check the **Actions** tab. I have updated the workflow to be more robust, and it should now successfully build the APK.

### 7. Troubleshooting Build Errors

#### "Could not find or load main class org.gradle.wrapper.GradleWrapperMain"
This means the `gradle-wrapper.jar` file is missing or corrupted in your repository.
**Solution:**
I have switched the GitHub Action to use the official **Setup Gradle** action which is much more reliable. If you see this error locally, simply run the `curl` command in Step 6 above to download a fresh jar.

#### "Minimum supported Gradle version is 8.4. Current version is 7.4.2."
If you see this error in Android Studio or terminal:
1. Ensure you are opening the **root folder** (the one containing `settings.gradle.kts`).
2. Make sure `gradle/wrapper/gradle-wrapper.properties` has `distributionUrl=...gradle-8.6-bin.zip`.
3. In Android Studio, go to **File > Settings > Build, Execution, Deployment > Gradle** and set "Gradle JDK" to **JDK 17**.
4. If building from terminal, always use `./gradlew assembleDebug` (Linux/Mac) or `gradlew.bat assembleDebug` (Windows) to ensure the correct version is used.

#### "No application modules found"
This happens if Android Studio doesn't recognize the project as a Gradle project.
1. Click **File > Sync Project with Gradle Files**.
2. If the option is missing, click **File > Close Project** and then **Open** the root folder again.

#### Setting up Signing Secrets (Required for Releases)
Add these secrets to your GitHub repository (**Settings > Secrets and variables > Actions**):

1. **KEYSTORE_BASE64**: Generate a keystore, then encode it:
   ```bash
   keytool -genkey -v -keystore openchat.jks -keyalg RSA -keysize 2048 -validity 10000 -alias openchat
   base64 -w 0 openchat.jks > encoded.txt
   # Copy content of encoded.txt to GitHub Secret
   ```
2. **KEYSTORE_PASSWORD**: The password for your keystore.
3. **KEY_ALIAS**: `openchat` (or your chosen alias).
4. **KEY_PASSWORD**: Your key password.

#### Trigger a Release
```bash
git tag v1.0.0
git push origin v1.0.0
```

## 📝 License
MIT License. See `LICENSE` for details.
