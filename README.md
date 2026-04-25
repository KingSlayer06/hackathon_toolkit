# Vox

Talk to your bunq. A voice-first interface that plans bunq sub-account moves,
recurring splits, and card guardrails — you approve the diff before a single
euro shifts.

## Components

| Component  | Tech                                                         | Where        |
| ---------- | ------------------------------------------------------------ | ------------ |
| Backend    | Python · FastAPI · bunq SDK · LLM planner · SQLite           | `backend/`   |
| Web UI     | React · Vite · TypeScript · Tailwind · framer-motion         | `frontend/`  |
| Mobile UI  | Kotlin Multiplatform · Compose Multiplatform · Ktor          | `vox_bunq_hackathon_70/` |
| API toolkit | Standalone bunq scripts                                      | `0X_*.py`    |

## Backend (FastAPI)

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env  # add bunq + LLM keys
uvicorn backend.main:app --reload --port 8080
```

## Frontend (web)

```bash
cd frontend
npm install
npm run dev   # localhost:5173, proxies /api -> :8080
```

## Mobile (Android + iOS via Compose Multiplatform)

Targets the same backend as the web frontend. Browser Web Speech is replaced by
native `android.speech.SpeechRecognizer` and `SFSpeechRecognizer`.

### Prereqs

- JDK 17 (set `JAVA_HOME`, e.g. `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`)
- Android SDK with platform 35 + build-tools 34 (Android Studio handles this)
- Xcode 15+ for iOS builds

All KMP files live in `vox_bunq_hackathon_70/`. `cd vox_bunq_hackathon_70` before
running any `./gradlew` command below.

### Backend URL

Defaults are platform-specific (in `vox_bunq_hackathon_70/composeApp/src/{androidMain,iosMain}/kotlin/com/kingslayer06/vox/data/Config.*.kt`):

- Android emulator → `http://10.0.2.2:8080` (host machine's loopback)
- iOS simulator → `http://localhost:8080`

For real devices on your LAN, override at app start:

```kotlin
com.kingslayer06.vox.data.VoxConfig.baseUrl = "http://192.168.1.42:8080"
```

### Run Android

```bash
cd vox_bunq_hackathon_70
./gradlew :composeApp:installDebug   # device or emulator running
# or open vox_bunq_hackathon_70/ in Android Studio and hit Run
```

### Run iOS

```bash
open vox_bunq_hackathon_70/iosApp/iosApp.xcodeproj
# pick a simulator/device and Run
# (the project's "Compile Kotlin Framework" build phase calls
#  `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automatically)
```

First iOS build downloads the K/N toolchain (~1 GB into `~/.konan`) — be patient.

### Mobile feature parity with the web frontend

- Hold-to-talk mic button (native speech recognizer per platform)
- Live transcript with edit-as-text fallback
- Plan → diff cards (transfer / recurring split / conditional freeze / tx limit) with checkbox-selected execution
- Sub-accounts panel with hot-flash on rule firing
- Cards panel with frozen-state mini card art
- Active rules with disarm
- SSE-powered live rule firing toasts
- Demo trigger buttons (salary / bar / large tx)
- Status pills (bunq / llm / events)

## API toolkit scripts

The numbered `0X_*.py` files are standalone bunq SDK examples (auth, accounts,
payments, cards, callbacks). They use `bunq_client.py` and live for quick
reference / debugging — they are not required by the app.
