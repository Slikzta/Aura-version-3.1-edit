# Aura Android Application - Download & Release Verification

### Direct Download Link
* **Direct Web Download Link**: [Download Aura APK (v1.0.3)](https://ais-dev-vfh2mhsbm2a6efumvrlxdt-881326756045.us-east1.run.app/app-debug.apk)
* **Download Landing Page**: [https://ais-dev-vfh2mhsbm2a6efumvrlxdt-881326756045.us-east1.run.app/](https://ais-dev-vfh2mhsbm2a6efumvrlxdt-881326756045.us-east1.run.app/)

---

### APK Specifications & Checksums
* **Application Name**: Aura
* **Application ID**: `com.aistudio.aura.vqpzkm`
* **Version Name**: `1.0.3`
* **Version Code**: `4`
* **Target SDK**: Android 16 (SDK 36)
* **Min SDK**: Android 7.0 (API 24)
* **File Size**: ~24.1 MB (24,065,709 bytes)
* **SHA-256 Checksum**:
  ```text
  f3c450510deae7688e0ee3eea26f2792c01151a4e130b1418a0bb6a38a4cf4d6
  ```

---

### Environment Variables Status (Verified)
* **OpenAI Secrets**: Successfully removed from container environment (`OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` are completely cleared).
* **Gemini Secrets**: Active and populated:
  * `GEMINI_API_KEY`: Active
  * `GEMINI_BASE_URL`: `https://generativelanguage.googleapis.com/v1beta`
  * `GEMINI_MODEL`: `gemini-3.6-flash`

---

### How to Install on Android Phone
1. Open the direct link on your phone:
   `https://ais-dev-vfh2mhsbm2a6efumvrlxdt-881326756045.us-east1.run.app/app-debug.apk`
2. If Android prompts that downloading APK files may be harmful, tap **Download anyway**.
3. Open the downloaded file. When prompted, allow **Install unknown apps** for your browser.
4. If Google Play Protect shows a warning (*"Unrecognized developer"*), tap **More details** and select **Install anyway**.
5. **Important**: If you previously had an APK installed from GitHub Actions, uninstall that version first to prevent Android's `INSTALL_FAILED_UPDATE_INCOMPATIBLE` signing key conflict.
