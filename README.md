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
   - Staging and Production require HTTPS. Development permits HTTP for development hosts and IP addresses (e.g. `localhost`, `10.0.2.2`, LAN IP addresses such as `192.168.x.x`).
   - Dashboard URLs derive `wss://` from HTTPS and `ws://` from HTTP in Development.
   - "Test connection" verifies `GET /health` (`{"status":"ok"}`). Changing the endpoint while authenticated is disabled; sign out first so session credentials cannot be sent to a different backend.

2. **Authentication**:
   - Administrators can access robot status, dashboard tickets, mapping, destinations, and robot controls. Standard users see their own deliveries and can use only the delivery actions authorized for their account; the backend remains authoritative for ownership and permissions.
   - JWT access tokens stored securely in memory with automated HTTP-only cookie refresh interception on 401.

3. **Backend and navigation scope**:
   - The current backend has no configured navigation transport. Dispatch, pickup/return, and cancellation requests return NAVIGATION_UNAVAILABLE until a transport is integrated. Delivery records and reported lifecycle states do not prove physical robot motion or a motor stop.
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

## UI completion checks (16 October 2026)

Admin primary navigation is Dashboard, Deliveries, and More. Mapping, Robot
console, and Destinations are available in More; Android Back returns from
these tools to More. Standard users retain Deliveries and More.

Destination creation requires an explicitly selected finished map and finite
coordinates. Failed saves retain the dialog and inputs; deletion requires
confirmation. Delivery creation requires deliberate destination selection and
creates a record separately from dispatch. Action confirmation follows the
reported lifecycle and retains backend errors. The backend remains authoritative
for authorization, map validity, and robot readiness.

Nonsecret login inputs and form drafts use saved state across recreation;
passwords are not saved. Status labels wrap with larger text and controls expose
button semantics. Device checks are still needed for small screens, large font
scales, TalkBack, keyboard focus, and rotation during pending requests.

From this checkout's `frontend` directory, run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests 'com.example.robotoperator.*'
```

This includes UiValidationTest for nonfinite coordinates, lifecycle action
availability, and compact role-specific navigation. See
`../assessment/UI_IMPLEMENTATION_PLAN.md` for the recorded validation outcome.

### Additional usability fixes

Events use backend severity filtering (including `WARN`) and load subsequent
pages. Refresh failures remain visible beside retained data. User creation
masks passwords and keeps failed submissions open. Robot access mutations and
account deactivation check unsuccessful HTTP responses; deactivation asks for
confirmation. Mapping creation prevents duplicate taps and failed completion
keeps its form open. Shared status badges use stronger text contrast, metadata
rows wrap within allocated widths, and event filters wrap on smaller screens.

### Plain JVM regression tests without native rendering

The tests in `com.example.robotoperator.*` use plain JUnit and do not use
Robolectric runners. If the large Robolectric native-renderer download is
unavailable, run this opt-in command from `frontend`:

```powershell
.\gradlew.bat --offline --no-configuration-cache -I ../assessment/pure-jvm-tests.init.gradle :app:testDebugUnitTest
```

The init script selects only those tests and excludes
`org.robolectric:nativeruntime-dist-compat` from that run's test runtime.
It does not change app dependencies or the normal test configuration. This is
not a substitute for screenshot, Robolectric, emulator, or device testing.

Mapping details discard refresh responses that predate a new action and show
refresh errors separately from action failures. The console blocks duplicate
requests, confirms finishing a session, preserves connection drafts, and scrolls
its control panels in short landscape layouts.

### User and Admin entry screens

The signed-out app opens a welcome screen with **User** and **Admin** choices.
Each opens a dedicated role-labelled sign-in journey. User accounts open My
deliveries; administrators open Dashboard and management tools. The account role
returned by `/auth/me` must match the selected journey before authentication is
published to the UI. Picking Admin does not assign administrator privileges.
Public registration is available only on the User journey and must return USER.

Connection settings are a secondary link, not the initial screen. If no backend
is configured, submitting a form gives a setup message without sending a network
request. A valid deployed HTTPS backend is still required for real sign-in.
