# AGENTS.md

Guidance and reference for AI coding agents and developers working on the Robot Operator native mobile operations Android codebase.

---

## 1. Project Overview & Operational Context

**Robot Operator** (`com.aistudio.robotoperator.rxptk`) is the native Android operations client for an Autonomous Indoor Delivery Robot backend (FastAPI + JWT + WebSockets). It enables operators and administrators to monitor robot telemetry, manage SLAM mapping sessions, configure indoor navigation destinations, dispatch delivery missions, and audit system events.

### Core Domain & Operational Principles
- **No Demoware Aesthetic**: Strictly light-themed, utilitarian industrial operations UI. Never add decorative dark mode, flashy animations, or non-functional aesthetics.
- **Color Semantics**:
  - `Green` (`StatusGreen` / `0xFF16A34A`): Backend-reported online / safe / tracking / active state. Freshness and physical safety are **never** implied by color alone.
  - `Amber` (`StatusAmber` / `0xFFD97706`): Paused / Slow / Relocating / Manual / Stale telemetry.
  - `Red` (`StatusRed` / `0xFFDC2626`): Stop / Lost / Emergency Stop / Fault / Revoked session.
  - `Grey` (`StatusGrey` / `0xFF64748B`): Offline / Idle / Finished.
- **Reported Telemetry vs. Physical State**: Telemetry, robot status, SLAM states, and pose trails represent backend-reported observations. A rendered map or pose trail does not establish live hardware telemetry, current physical safety, or validated physical mapping. Feed disconnection marks observations `STALE`, not physically `OFFLINE`.
- **Emergency Stop Safety Semantics**: The E-stop control sends a request to the backend. The UI only preserves and displays a physical-execution confirmation when the backend response explicitly returns `physical_execution_confirmed: true`. Never infer a verified motor stop from a command request, status change, or mock navigation outcome.
- **Role Authority**: The backend remains authoritative for ownership and permissions. Standard `USER` accounts can access only their own deliveries and authorized actions; `ADMIN` accounts manage robot status, mapping, destinations, live console, and user access.

---

## 2. Technical Stack & Dependencies

| Layer | Technology | Details |
|---|---|---|
| **Language & Platform** | Kotlin 2.2.10 | Target JVM 11 / Compile against Android SDK 36.1 (`compileSdk = 36`, `minorApiLevel = 1`) |
| **Android SDK Support** | Min SDK 24, Target SDK 36 | Android Gradle Plugin (AGP) `9.1.1` |
| **Build System** | Gradle 9.3.1 Wrapper | Pinned wrapper with checksum verification (`gradle/wrapper/gradle-wrapper.properties`) |
| **UI Framework** | Jetpack Compose | Compose BOM `2024.09.00`, Material 3 (`1.3.0`+), Activity Compose `1.10.1`, Compose Navigation `2.8.9` |
| **Lifecycle & State** | AndroidX Lifecycle `2.8.7` | `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, Kotlin Coroutines `1.10.2` & `StateFlow` |
| **Networking & HTTP** | Retrofit `2.12.0` + OkHttp `4.10.0` | `converter-moshi`, `logging-interceptor` (BASIC level), custom `ScopedCookieJar` |
| **JSON Serialization** | Moshi `1.15.2` | KSP code generation (`@JsonClass(generateAdapter = true)`) via Google KSP `2.3.5` |
| **Real-time Telemetry** | OkHttp WebSocket | Authenticated via short-lived dashboard tickets (`POST /dashboard/ws-ticket`), bounded backoff |
| **Preferences & Storage** | Android `SharedPreferences` | In-memory token storage for JWT; endpoints/identifiers in `ServerSettingsStore` |
| **Testing** | JUnit 4 (`4.13.2`), AndroidX Test | `kotlinx-coroutines-test` `1.10.2`, Robolectric `4.16.1`, Roborazzi `1.59.0` (screenshot tests) |
| **Build Plugins** | KSP, Secrets, Google Services | Secrets Gradle Plugin (`2.0.1`), Google Services (`4.5.0` with missing passthrough) |

---

## 3. Repository Architecture & Layout

The codebase follows a single-activity architecture with layered data, state, and declarative UI components:

```
frontend/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml                  # Manifest with network security config & permissions
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt                  # Single activity entry point and OperatorAppRoot
│   │   │   │   ├── ui/theme/                        # Utilitarian design tokens (Color, Theme, Type)
│   │   │   │   │   ├── Color.kt                     # Slate scales, Status colors, ActionBlue, SurfaceCanvas
│   │   │   │   │   ├── Theme.kt                     # Light-only RobotOperatorTheme & TechnicalShapes
│   │   │   │   │   └── Type.kt                      # Monospace data labels and technical typography
│   │   │   │   └── robotoperator/
│   │   │   │       ├── data/
│   │   │   │       │   ├── api/                     # Retrofit & OkHttp networking
│   │   │   │       │   │   ├── ApiClient.kt         # In-memory JWT holder, 401 cookie refresh, ScopedCookieJar
│   │   │   │       │   │   ├── ApiErrorParser.kt    # Structured backend error parser
│   │   │   │       │   │   ├── ApiService.kt        # Retrofit REST interface
│   │   │   │       │   │   ├── DashboardConnectionSupport.kt  # Ticket URL builder, backoff, disconnect actions
│   │   │   │       │   │   ├── DashboardWebSocketClient.kt    # OkHttp WebSocket telemetry stream client
│   │   │   │       │   │   └── RobotConnectionSupport.kt      # Private IPv4 & port validator for local bridge
│   │   │   │       │   ├── model/                   # Domain entities, DTOs, and business logic
│   │   │   │       │   │   ├── Models.kt            # Moshi JSON data classes (RobotStatus, Pose, Delivery, etc.)
│   │   │   │       │   │   ├── DeliveryProgress.kt  # Delivery lifecycle progression helpers
│   │   │   │       │   │   ├── RobotStatusSemantics.kt # Connection state normalization and safety label context
│   │   │   │       │   │   ├── RolePermissions.kt   # Role checks (ADMIN vs USER) and tab/action visibility
│   │   │   │       │   │   └── UiValidation.kt      # Coordinate & delivery action state checkers
│   │   │   │       │   └── prefs/
│   │   │   │       │       ├── BackendEndpointPolicy.kt # URL validator (HTTPS mandatory except local dev)
│   │   │   │       │       └── ServerSettingsStore.kt   # Persistent endpoint, robot ID, and session settings
│   │   │   │       ├── state/                       # Reactive state holders
│   │   │   │       │   ├── AuthManager.kt           # Session lifecycle, login/register/logout, role gating
│   │   │   │       │   ├── LoginRole.kt             # Portal choice vs. server role verification
│   │   │   │       │   └── RobotStateHolder.kt      # StateFlow holder for status, pose, 500-pt trail, E-stop
│   │   │   │       └── ui/
│   │   │   │           ├── MainScaffold.kt          # Primary navigation, StatusStrip, EStopBanner, tabs & sub-screens
│   │   │   │           ├── components/              # Shared industrial UI components
│   │   │   │           │   ├── EStopBanner.kt       # Prominent banner for E-stop / physical execution state
│   │   │   │           │   ├── KeyValRow.kt         # Monospace key-value telemetry row
│   │   │   │           │   ├── ModeSelectionDialog.kt # Modal for MANUAL / AUTONOMOUS control switching
│   │   │   │           │   ├── PosePlotCanvas.kt    # 2D Canvas rendering robot pose and historical trail
│   │   │   │           │   ├── StatusBadge.kt       # High-contrast color-coded status badge
│   │   │   │           │   └── StatusStrip.kt       # Persistent top telemetry & E-stop action strip
│   │   │   │           └── screens/                 # Screen implementations
│   │   │   │               ├── auth/LoginScreen.kt  # User/Admin split sign-in and registration
│   │   │   │               ├── dashboard/DashboardScreen.kt # Live telemetry, pose trail, status metrics
│   │   │   │               ├── deliveries/          # DeliveriesScreen & DeliveryDetailScreen
│   │   │   │               ├── destinations/        # DestinationsScreen (finite coords, map-backed)
│   │   │   │               ├── mapping/             # LiveMappingConsoleScreen, MappingListScreen, MappingDetailScreen
│   │   │   │               ├── more/                # MoreScreen, EventsScreen (audit logs), UsersScreen
│   │   │   │               └── server/ServerSettingsScreen.kt # Environment & backend URL configuration
│   │   │   └── res/                                 # Drawables, mipmaps, XML security configurations
│   │   └── test/java/com/example/                   # Unit, Robolectric, and Roborazzi screenshot tests
│   ├── build.gradle.kts                             # Module configuration, dependencies, signing configs
│   └── proguard-rules.pro                           # Proguard optimization rules
├── gradle/
│   ├── libs.versions.toml                           # Pinned dependencies and plugins version catalog
│   └── wrapper/                                     # Gradle wrapper jar and properties
├── build.gradle.kts                                 # Root build configuration
├── settings.gradle.kts                              # Plugin repositories and project inclusion
├── gradle.properties                                # Memory settings, daemon configs, worker constraints
├── .env.example                                     # Template for optional build endpoint suggestions & Gemini API key
├── README.md                                        # Developer and operator documentation
└── STAGING_ACCEPTANCE.md                            # Detailed staging acceptance verification runbook
```

---

## 4. Key Architectural Patterns & Security Constraints

### A. Environment Selection & Strict Network Security
1. **No Implicit Server Requests**: A fresh install makes **zero** network requests until an operator explicitly selects and saves an environment endpoint in Server Settings.
2. **Environment Isolation**:
   - `DEVELOPMENT`: Permits cleartext HTTP **only** for `localhost`, `127.0.0.1`, and Android Emulator host alias `10.0.2.2`. All other hosts are rejected.
   - `STAGING` & `PRODUCTION`: Strictly require `HTTPS` (and derived `wss://`). Any cleartext HTTP is rejected.
   - Manifest configuration (`android:usesCleartextTraffic="false"`) strictly enforces cleartext limits via `network_security_config.xml`.
3. **Endpoint Immutability During Active Session**: Changing the server endpoint while authenticated is locked in the UI. Operators must sign out before changing the backend to prevent cross-server credential leakage.
4. **No Secrets in Endpoints**: Credentials (usernames, passwords, tokens, query params) are strictly prohibited in backend URLs.

### B. Authentication & Token Management
1. **In-Memory JWT Access Tokens**: The access token is held strictly in memory in `ApiClient.kt`. It is **never** persisted to `SharedPreferences`, Room, SQLite, or disk.
2. **HttpOnly Cookie Refresh**: Refresh tokens are managed via OkHttp's `ScopedCookieJar`. On HTTP 401, OkHttp interceptor/authenticator attempts a synchronized refresh via `POST /auth/refresh`.
3. **Session Revocation**: Local logout immediately clears in-memory tokens and cookies. Server-side revocation is executed via `POST /auth/logout`. If the server revocation call fails, the user is still signed out locally and informed with an explicit notice.
4. **Portal Matching**: The login screen provides separate **User** and **Admin** portals. The role returned by `GET /auth/me` must strictly match the portal chosen (`matchesLoginRole(...)`); otherwise, credentials are wiped and access is denied.

### C. Role-Based Access Control (RBAC)
- **`ADMIN` Role**: Access to Dashboard (`/robot/status`, WebSocket telemetry), Mapping (`/mapping/*`), Live Console, Destinations (`/destinations`), Audit Events (`/events`), and User Management (`/admin/users`).
- **`USER` Role**: Access restricted to Deliveries (`/deliveries/mine`) and delivery pickup confirmation (`/deliveries/{id}/confirm-pickup`). Never allow standard users to invoke admin endpoints or request dashboard tickets. Unauthorized attempts returning HTTP 403 show an error notice and **must not** force logout.

### D. WebSocket Telemetry Stream & Ticket Lifecycle
1. **Ticket Exchange**: The mobile client never sends JWT access tokens over WebSocket connection URLs. It calls authenticated HTTPS `POST /dashboard/ws-ticket` to obtain a short-lived, single-use ticket, and connects to `wss://<host>/ws/dashboard?ticket=<ticket>`.
2. **Reconnection & Disconnect Policies**:
   - Close code `4003` (forbidden/invalid grant): Reconnect stops immediately; requests re-authentication.
   - Close code `4001` or network disconnect: Reconnects using bounded exponential backoff (max 5 retries, capped at 16s delay). Each reconnection attempt obtains a **fresh** ticket.

### E. Reactive State Management
- `RobotStateHolder` holds single sources of truth using Kotlin `StateFlow`.
- Pose trails are dynamically capped to **500 points** to prevent unbounded memory growth on long-running operations.
- State preservation across configuration changes uses `rememberSaveable` for UI drafts, selections, and search terms. **Passwords must never be saved in `rememberSaveable`**.

---

## 5. Build, Test & Verification Commands

All commands should be executed from the workspace root (`frontend/`) using PowerShell or standard shell.

### Prerequisites
- **JDK 17**: Default Java runtime for Gradle build, packaging, and JVM unit tests.
- **JDK 21**: Required when executing Robolectric or Roborazzi screenshot tests on Android SDK 36.
- **Android SDK**: Build Tools `36.0.0`, Platform `android-36.1`, Platform Tools. Ensure `local.properties` contains `sdk.dir=<path to android sdk>`.

### Build Commands
```powershell
# Verify Gradle wrapper version and environment
.\gradlew.bat --version

# Assemble debug APK
.\gradlew.bat :app:assembleDebug

# Assemble release APK (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD)
.\gradlew.bat :app:assembleRelease
```

### Unit Test Commands
```powershell
# Run all unit tests for the robot operator module (JVM JUnit tests)
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.robotoperator.*'

# Run the focused unit test suite (contracts, websockets, semantics, logout)
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.robotoperator.data.api.DashboardConnectionSupportTest' `
  --tests 'com.example.robotoperator.data.api.FrontendContractTest' `
  --tests 'com.example.robotoperator.state.LogoutConfirmationTest' `
  --tests 'com.example.robotoperator.state.RobotStatusSemanticsTest' `
  --tests 'com.example.ExampleUnitTest'

# Run Robolectric test (requires JDK 21)
$env:JAVA_HOME = '<path to JDK 21>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.ExampleRobolectricTest'

# Verify Roborazzi screenshot tests (requires JDK 21)
.\gradlew.bat :app:verifyRoborazziDebug --tests 'com.example.GreetingScreenshotTest'

# Record new screenshot baselines
.\gradlew.bat :app:recordRoborazziDebug --tests 'com.example.GreetingScreenshotTest'

# Offline JVM test execution using pure-jvm init script (avoids Robolectric downloads)
.\gradlew.bat --offline --no-configuration-cache -I ../assessment/pure-jvm-tests.init.gradle :app:testDebugUnitTest
```

---

## 6. Coding Conventions & Best Practices

### A. Kotlin & Compose Standards
- **Official Code Style**: Follow `kotlin.code.style=official` as configured in `gradle.properties`.
- **Composable Signatures**:
  - Functions must be in `PascalCase`.
  - Always accept a `modifier: Modifier = Modifier` parameter for customizable components and place it as the first optional parameter.
  - Expose state downwards via parameters; bubble events upwards via lambdas (`onAction: () -> Unit`).
- **Semantic Test Tags**: Every button, navigation tab, input field, and modal must specify `.testTag("<descriptive_tag>")` for reliable automated testing.
- **Back Navigation**: Always implement explicit `BackHandler` on sub-screens and dialogs to handle system back button presses without unexpected app exits.

### B. Networking & Error Handling
- **Never swallow `CancellationException`**: In coroutine blocks, always catch and rethrow `CancellationException` to avoid breaking Compose coroutine job cancellation:
  ```kotlin
  try {
      apiClient.getService().doSomething()
  } catch (e: CancellationException) {
      throw e
  } catch (t: Exception) {
      errorMessage = ApiErrorParser.parse(t).message
  }
  ```
- **Error Parsing**: Always route network exceptions through `ApiErrorParser.parse(throwable)` to extract structured server messages (`ParsedError`), handling specific status codes (401, 403, 404, 422, 423, 429, 501) cleanly.
- **Preserve Drafts on Error**: Never clear form drafts, dialog inputs, or selections when a submission fails. Keep the modal or form open and display the backend error message inline.

### C. Validation & Safety Rules
- **Coordinates**: Use `finiteCoordinate(input)` to sanitize and validate coordinates before sending to the backend. Reject `NaN`, `Infinity`, empty strings, and non-numeric characters.
- **Destinations**: Require an explicit finished map (`is_home` or map ID from a `FINISHED` mapping session).
- **Deliveries**: Deliveries are created separately from dispatch. Only allow dispatch when status is `IDLE`. Only allow pickup confirmation when status is `ARRIVED`.
- **E-Stop Texting**: Wording must remain "BACKEND E-STOP STATE" or "E-Stop Requested" unless `physical_execution_confirmed: true` is reported. Never claim physical safety has been verified.

### D. Comments & Integrity
- Maintain existing comments and docstrings. Do not remove safety warnings, operational disclaimers, or design rationale when editing code.
- Avoid adding speculative abstractions or unused utility classes. Keep solutions minimal, direct, and aligned with industrial operations requirements.
