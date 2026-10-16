# Robot Operator - Native Mobile Operations App

Internal operations mobile application for the Autonomous Indoor Delivery Robot backend (FastAPI + JWT + WebSocket).

## Operations Design Principles
- **No Demoware Aesthetic**: Strictly light-themed, utilitarian industrial operations UI.
- **Color Semantics**: 
  - Green = backend-reported online/safe/tracking/active state; freshness and physical safety are not implied by color alone
  - Amber = Paused / Slow / Relocating / Manual
  - Red = Stop / Lost / Emergency Stop / Fault
  - Grey = Offline / Idle / Finished
- **Emergency stop request**: The administrator control sends a request to the backend. The UI displays backend/application state and only preserves a physical-execution confirmation when the backend explicitly returns `physical_execution_confirmed`; this repository has not verified physical stop execution.
- **Reported telemetry and maps**: Robot status and pose are backend-reported observations and may be stale or unavailable. A rendered map or pose trail does not establish live hardware telemetry, current physical safety, or validated physical mapping.

## Configuration & Network Setup
1. **Backend environment**:
   - Fresh installs have no selected backend and make no network requests until an operator selects and saves an environment endpoint.
   - Development, Staging, and Production are selected explicitly in Server Settings. Production is never selected automatically.
   - Optional non-secret endpoint suggestions can be supplied with Gradle properties or environment variables named `DEVELOPMENT_BACKEND_URL`, `STAGING_BACKEND_URL`, and `PRODUCTION_BACKEND_URL`. All default to empty. A supplied suggestion does not select that environment.
   - Staging and Production require HTTPS. Development permits HTTP only for `localhost`, `127.0.0.1`, or Android Emulator host alias `10.0.2.2`; arbitrary LAN and remote HTTP endpoints are rejected.
   - Dashboard URLs derive `wss://` from HTTPS. The loopback Development exception derives `ws://` only for the explicitly allowed local hosts. Cleartext is disabled by default in the Android manifest and narrowly permitted for those local development domains.
   - "Test connection" verifies `GET /health` (`{"status":"ok"}`). Changing the endpoint while authenticated is disabled; sign out first so session credentials cannot be sent to a different backend.

2. **Authentication**:
   - Administrators can access robot status, dashboard tickets, mapping, destinations, and robot controls. Standard users see their own deliveries and can use only the delivery actions authorized for their account; the backend remains authoritative for ownership and permissions.
   - JWT access tokens stored securely in memory with automated HTTP-only cookie refresh interception on 401.

3. **Backend and navigation scope**:
   - Delivery navigation in the current backend is provided by its mock navigation adapter. Delivery status changes and mock navigation outcomes are application state, not proof of physical robot motion or a motor stop.
   - This mobile app does not provide evidence that hardware telemetry, localization, mapping, or physical safety has been validated. Treat dashboard values as reported telemetry and observe their freshness/connection labels.

## Reproducible Build and Tests

### Clean checkout requirements

- Use JDK 17 for the Android build with the Gradle 9.3.1 wrapper. The wrapper downloads the pinned Gradle distribution from `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip` on first use, so that host and the configured Gradle plugin and dependency repositories must be reachable.
- Install Android SDK Platform 36.1 (`platforms;android-36.1`), Android SDK Build-Tools 36.0.0 (`build-tools;36.0.0`), and Android SDK Platform-Tools (`platform-tools`). The app compiles with API 36, minor API level 1, and requires minimum API 24.
- Configure the local Android SDK path in an untracked `local.properties` file (`sdk.dir=...`) or open the project in Android Studio and select the installed SDK. Never commit `local.properties`.
- Debug builds use Android Gradle Plugin's standard user-local debug signing key; no keystore belongs in the repository. Release signing uses `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`, which must be supplied through a protected release process and are not needed for local debug builds.
- Optional endpoint suggestions are configured as described above. They do not select an environment. Do not put credentials in Gradle properties or endpoint URLs.
- The Google Services plugin warns when `app/google-services.json` is absent; the current build allows it to be missing. Firebase-backed features may require the project-specific file supplied through the team's approved process.

### Build and focused unit tests (Windows PowerShell)

From the repository root:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.robotoperator.data.api.DashboardConnectionSupportTest' --tests 'com.example.robotoperator.data.api.FrontendContractTest' --tests 'com.example.robotoperator.state.LogoutConfirmationTest' --tests 'com.example.robotoperator.state.RobotStatusSemanticsTest' --tests 'com.example.ExampleUnitTest'
```

The focused test command runs those five classes only. It does not run screenshot tests.

The wrapper scripts and `gradle/wrapper/gradle-wrapper.jar` should be version-controlled with `gradle/wrapper/gradle-wrapper.properties`. The wrapper pins Gradle 9.3.1 and configures `distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`, verified against Gradle's official [distribution checksum reference](https://gradle.org/release-checksums/).

### Robolectric on Android SDK 36

`ExampleRobolectricTest` is separate from the focused test command above. On the prior Java 17 run, Robolectric failed because Android SDK 36 requires Java 21. Set `JAVA_HOME` to an installed JDK 21 before running this test; do not change the project's Gradle or Android SDK versions to bypass the requirement:

```powershell
$env:JAVA_HOME = '<path to JDK 21>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.ExampleRobolectricTest'
```

This command has not been verified on JDK 21. Screenshot tests are not included in it.
