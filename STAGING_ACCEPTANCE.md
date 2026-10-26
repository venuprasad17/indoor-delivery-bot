# Cloud staging acceptance checklist

**Status: UNVERIFIED.** No Render service or managed staging database/cache was accessed while preparing this checklist. Record dated evidence for each check before changing its status.

## Managed MySQL and Redis

- [ ] Configure staging-only managed MySQL and Redis connection URLs with TLS enabled and certificate/hostname validation enabled. Record provider TLS settings and application startup logs with connection strings and credentials redacted.
- [ ] Confirm the backend connects successfully to each staging dependency over TLS. A successful TCP connection alone is insufficient evidence of certificate validation.
- [ ] Apply the documented Alembic chain only to the staging database after taking a restorable backup. Verify required trigger privileges, migration revision, schema integrity, and the documented restore procedure.

## Startup, health, and recovery

- [ ] Start the Render staging service with `ROSBRIDGE_ENABLED=false`, `SIMULATION_MODE=false`, mock navigation selected, and `RUN_MIGRATIONS_ON_STARTUP=false` unless the operator has explicitly approved the documented deployment migration process. Capture startup and shutdown logs with secrets redacted.
- [ ] Confirm `GET /health` returns HTTP 200 while the service process is live.
- [ ] Confirm `GET /ready` returns HTTP 200 when both required database and Redis dependencies are available.
- [ ] Independently make the staging database unavailable and then Redis unavailable using provider-supported staging controls. For each case, confirm `/ready` returns HTTP 503, `/health` remains a liveness response, and logs identify the dependency category without exposing connection details.
- [ ] Restore each dependency independently and confirm readiness returns to HTTP 200 without a false healthy state during recovery.
- [ ] Restart or stop the staging service gracefully. Confirm application shutdown closes database and Redis resources and emits no unhandled shutdown errors.

## Ingress and dashboard tickets

- [ ] Use a disposable staging administrator account and synthetic dashboard ticket. Verify the ticket can be used only once and expires as configured.
- [ ] Inspect Render ingress/access logs and any configured proxy logs. Confirm the ticket query parameter is absent or redacted in every log destination. Do not copy the ticket into the evidence report.
- [ ] Confirm dashboard WebSocket authentication uses `POST /dashboard/ws-ticket` over authenticated HTTPS followed by the returned ticket on WSS. Never place an access JWT in a WebSocket URL.

## Frontend/backend acceptance

- [ ] Verify an administrator can log in, obtain a dashboard ticket for each connection, reconnect with a new ticket, and receive a distinct message for authentication failure (401) versus authorization denial (403).
- [ ] Verify logout revokes the session, prevents new ticket issuance, rejects outstanding tickets, and closes an existing dashboard WebSocket according to the implemented backend revocation policy.
- [ ] Verify a standard USER is routed to their own deliveries and does not call `/robot/status`, `/dashboard/ws-ticket`, admin destinations, or the administrator dashboard. Confirm unauthorized requests remain 403 and do not force logout.
- [ ] Verify delivery ownership, pickup confirmation, dispatch/cancellation permissions, and expected API response handling against the staging backend.
- [ ] Disconnect dashboard feed delivery while robot telemetry previously reported `ONLINE` and `SAFE`. Confirm the UI marks the observation stale/unknown and does not infer physical offline state or current physical safety from the feed loss.
- [ ] Verify emergency-stop wording remains a backend request unless an explicit backend `physical_execution_confirmed` value is returned. Do not infer verified motor stop from a request, cancellation, mock navigation result, or status transition.

## Evidence record

For every completed item, record the staging service/environment identifier, UTC timestamp, operator, redacted request/response or log evidence, and pass/fail result. Keep all items UNVERIFIED until this evidence exists. Do not include credentials, tokens, tickets, user personal data, or production data.

## Phase 5.5 access check and current evidence

Access precheck: **UNAVAILABLE** as of `2026-10-15 06:22 UTC`. Sanitized evidence: the local runtime had no Render connector and no staging endpoint or staging credential variables configured. No service URL was contacted. No staging service, database, Redis instance, ingress log, or runtime was accessed. Consequently, no staging behavior is claimed as verified.

| Check | Status | Timestamp (UTC) | Evidence / operator note |
| --- | --- | --- | --- |
| Authorized staging access | UNAVAILABLE | 2026-10-15 06:22 | No Render connector or staging configuration was available in this runtime. An authorized operator must provide access through the approved deployment account/runtime. |
| MySQL TLS and certificate/hostname validation | UNVERIFIED | 2026-10-15 06:22 | Not inspected; capture provider TLS settings and sanitized connection evidence from staging. |
| Redis TLS and certificate/hostname validation | UNVERIFIED | 2026-10-15 06:22 | Not inspected; capture provider TLS settings and sanitized connection evidence from staging. |
| `/health` and `/ready` while dependencies are healthy | UNVERIFIED | 2026-10-15 06:22 | No staging requests made. Record sanitized HTTP status and timestamp for each endpoint. |
| `/ready` behavior for independent MySQL and Redis failures and recovery | UNVERIFIED | 2026-10-15 06:22 | Not attempted. Requires explicit operator approval and an approved recovery plan before any controlled dependency failure. |
| Graceful shutdown and service exit | UNVERIFIED | 2026-10-15 06:22 | No service restart or stop was performed. Operator must capture sanitized shutdown logs and exit status. |
| Migration privileges, backup, and restore evidence | UNVERIFIED | 2026-10-15 06:22 | No staging database or migration was accessed. Operator must review grants and provide backup/restore evidence; do not run migrations without separate approval. |
| Dashboard ticket ingress/access-log redaction | UNVERIFIED | 2026-10-15 06:22 | No ticket issued and no Render/proxy logs accessed. An authorized operator should use a synthetic short-lived ticket and record only whether the query value was redacted, never the value itself. |

### Operator runbook before resuming validation

1. An authorized operator selects the Render staging service and confirms it is isolated from production, with `ROSBRIDGE_ENABLED=false`, `SIMULATION_MODE=false`, mock navigation, and startup migrations disabled unless separately approved.
2. Provide access through the approved Render account or staging operations environment. Do not paste credentials, database URLs, tokens, or tickets into this document or chat.
3. Collect the healthy dependency, TLS, `/health`, `/ready`, and startup evidence first. For dependency-failure tests, obtain explicit approval and document a recovery owner and recovery steps before changing service availability.
4. Obtain migration privilege and existing backup/restore evidence from the database operator; do not execute a migration as part of this runbook without separate approval.
5. For ingress redaction, issue a synthetic one-use ticket through the normal authenticated flow, inspect authorized logs, and record only redacted evidence and the PASS/FAIL outcome.
6. Update a row above only after direct staging evidence is collected. Keep inaccessible or unperformed checks UNVERIFIED and record failures and recovery actions without sensitive values.

## Phase 5.6 local security and integration review

Review timestamp: `2026-10-15 06:33 UTC`. Staging access remains unavailable. No service URL was contacted; the entries below do not establish live frontend/backend integration.

### Local test evidence

Command executed:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --tests 'com.example.robotoperator.data.api.DashboardConnectionSupportTest' --tests 'com.example.robotoperator.data.api.FrontendContractTest' --tests 'com.example.robotoperator.state.LogoutConfirmationTest' --tests 'com.example.robotoperator.state.RobotStatusSemanticsTest' --tests 'com.example.ExampleUnitTest'
```

Result: **PASS**, `BUILD SUCCESSFUL`; fresh XML reports show 28 tests, 0 failures, 0 errors, 0 skipped (DashboardConnectionSupportTest 13; FrontendContractTest 7; LogoutConfirmationTest 3; RobotStatusSemanticsTest 4; ExampleUnitTest 1). These are local unit tests only. Roborazzi finalization was skipped; screenshot tests were not run.

Debug assembly command:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Result: **PASS**, `BUILD SUCCESSFUL in 51s`; 39 actionable tasks, 3 executed and 36 up-to-date. This is a local build, not a staging deployment.

### Source inspection evidence (not live verification)

| Contract | Status | Evidence source and scope |
| --- | --- | --- |
| Ticket exchange, URL, and per-connection freshness | PASS (source inspection) | Android `ApiService`/`DashboardWebSocketClient` obtains a ticket through authenticated `POST /dashboard/ws-ticket` for each connection, then uses only `?ticket=...`; `dashboardConnectionUrl` validates endpoint before requesting the ticket. Unit coverage includes fresh tickets, HTTPS-to-WSS conversion, and the close-code policy. |
| Ticket expiry, revocation, and reconnect behavior | PASS (source inspection + local unit tests) | Android reconnects with a newly issued ticket for retryable close code 4001 and stops for authentication on 4003; retries are bounded. Backend ticket path uses a short TTL, atomic one-use redemption, and session-revocation checks; authenticated dashboard sockets recheck revocation and close on revocation-store failure. Unit tests cover policy mapping and bounded retry, not the live backend or socket. |
| Ticket/JWT logging | PASS (source inspection) | WebSocket URL is built with the ticket and no access JWT; reviewed logging paths do not log the ticket or authenticated body. HTTP logging is BASIC. This does not verify proxy/ingress logs. |
| Login/refresh/logout and failed revocation | PASS (source inspection + local unit tests) | Access token is held in app authentication state, refresh is handled by the authenticated HTTP client, logout clears local auth but reports server revocation confirmation separately. Local logout tests exercise confirmation/failure behavior, not Redis-backed revocation. |
| HTTP 401 vs 403 | PASS (source inspection + local unit tests) | Authentication failure clears/recovers the session as appropriate; 403 is surfaced as authorization denial and does not force logout. Contract tests cover local response classification. |
| USER and ADMIN flow | PASS (source inspection + local unit tests) | USER entry routes to My Deliveries; its flow uses the user's delivery endpoint and does not enter the administrator dashboard, request dashboard tickets, or load admin destinations. Admin dashboard and administrative actions remain available to ADMIN. Backend role/ownership checks remain authoritative. |
| Freshness and safety wording | PASS (source inspection + local unit tests) | Dashboard feed loss marks the observation stale, not the robot physically offline. Stale/missing safety is presented as last reported/unknown; local semantics tests cover telemetry states. |
| E-stop and navigation claims | PASS (source inspection) | UI presents E-stop as a backend request unless `physical_execution_confirmed` is explicitly returned. Delivery navigation uses the mock adapter; this is not ROS/Nav2 execution. No physical stop or navigation was tested. |

### Staging acceptance status after Phase 5.6

All rows below remain **UNVERIFIED** because each requires an authorized staging service, test identity, managed dependencies, or ingress logs. Required evidence is direct, sanitized staging evidence with UTC timestamp and environment identifier.

| Check | Status | Evidence still required |
| --- | --- | --- |
| Staging login and refresh | UNVERIFIED | Operator-run login and refresh using staging test account; record sanitized status/outcome and session continuity. |
| Ticket expiry, one-use redemption, and reconnect | UNVERIFIED | Exercise ticket expiry and replay in staging; observe reconnection requesting a new ticket without recording its value. |
| Logout/session revocation and active socket closure | UNVERIFIED | Logout in staging; verify new ticket issuance and outstanding ticket redemption fail and existing socket closes per backend policy. |
| USER/ADMIN live permissions | UNVERIFIED | Exercise both staging roles; capture endpoint status codes and confirm USER receives 403 for forbidden resources without being logged out. |
| Stale telemetry presentation from live feed | UNVERIFIED | In staging, observe `ONLINE`/safety telemetry, stop dashboard feed delivery, and record stale/last-reported wording; do not infer physical robot state. |
| Render ingress/access-log ticket redaction | UNVERIFIED | Authorized operator inspects every relevant ingress/proxy log using a synthetic short-lived ticket and records only whether query value is absent/redacted. |
| MySQL/Redis TLS, health/readiness, outage/recovery, graceful shutdown, migrations/backup | UNVERIFIED | Complete the operator runbook above with provider TLS validation, endpoint statuses during healthy/failure/recovery, shutdown evidence, and reviewed migration grants plus backup/restore evidence. |

Java 21 is available in Android Studio's bundled JBR (`21.0.6`); the system-default Java remains `17.0.18`. The earlier statement that Java 21 was unavailable was incorrect and is superseded by the Phase 5.7 evidence below. No staging status is changed by local tests/builds.

## Phase 5.7 local test coverage

Review timestamp: `2026-10-15 06:56 UTC` (local test session). No staging URL was contacted.

### Environment and configured screenshot task

- Gradle wrapper is pinned to `9.3.1`; the locally installed distribution reports Gradle `9.3.1`. Android SDK `local.properties` points to the installed SDK, which includes platforms 35, 36, and 36.1 and Build Tools 36.0.0. These are local configuration findings.
- System Java is Temurin `17.0.18`; Android Studio bundled JBR is `21.0.6` and was explicitly selected for Robolectric/screenshot invocations.
- Gradle task discovery command: `:app:tasks --all --no-daemon`. Configured screenshot verification task: `:app:verifyRoborazziDebug` (task type `RoborazziTask`). The screenshot test is `GreetingScreenshotTest`, which writes to `app/src/test/screenshots/greeting.png` when capturing. Verification was limited to that class.

### Test and build results

Fresh focused test command, run using the local Gradle 9.3.1 installation (the wrapper attempted a distribution download but network access was denied). `$gradle931` refers to the operator's local Gradle 9.3.1 executable and is intentionally not a committed machine path:

```powershell
& $gradle931 :app:testDebugUnitTest --no-daemon --rerun-tasks --tests 'com.example.robotoperator.data.api.DashboardConnectionSupportTest' --tests 'com.example.robotoperator.data.api.FrontendContractTest' --tests 'com.example.robotoperator.state.LogoutConfirmationTest' --tests 'com.example.robotoperator.state.RobotStatusSemanticsTest' --tests 'com.example.ExampleUnitTest'
```

Result: **PASS**, 28 tests, 0 failures, 0 errors, 0 skipped. Per-class counts: DashboardConnectionSupportTest 13; FrontendContractTest 7; LogoutConfirmationTest 3; RobotStatusSemanticsTest 4; ExampleUnitTest 1. Gradle warned that `app/google-services.json` is absent; the selected tests still executed successfully.

Robolectric command, with `JAVA_HOME` set to Android Studio's JDK 21:

```powershell
& $gradle931 :app:testDebugUnitTest --no-daemon --tests 'com.example.ExampleRobolectricTest'
```

Result: **PASS**, 1 test, 0 failures, 0 errors, 0 skipped.

Screenshot verification command, with JDK 21:

```powershell
& $gradle931 :app:verifyRoborazziDebug --tests 'com.example.GreetingScreenshotTest' --no-daemon
```

Result: **FAIL**, the screenshot test executed and reported that the rendered image differs from `app/src/test/screenshots/greeting.png`; the compare output is under ignored `app/build/outputs/roborazzi/greeting_compare.png`. The checked-in reference was not changed: its SHA-256 before and after was `19799B48A74BA4134F68AE5A9A65F14158D8818CBFBB51F46EA40653F579D825`. No screenshot was recorded or accepted.

Debug assembly command:

```powershell
& $gradle931 :app:assembleDebug --no-daemon
```

Result: **PASS**, `BUILD SUCCESSFUL`; 39 tasks were up-to-date.

The five focused classes and the Robolectric class passed; the screenshot verification did not. Existing Kotlin warnings were emitted for deprecated OkHttp request-body creation, two Java nullability type mismatches in `ApiErrorParser.kt`, and deprecated `Icons.Filled.ListAlt`. A KSP AWT event-thread `NullPointerException` appeared during forced recompilation but did not fail compilation or the selected tests. These warnings remain unresolved.

### Disconnect policy review

- Close code `4001` maps to retry; the reconnect path calls ticket issuance again, so a new ticket is requested. Other retryable disconnects follow the same route.
- Retries are bounded to five attempts with exponential delays capped at 16 seconds.
- Close code `4003` stops reconnecting and requests authentication. HTTP 401 follows the sign-in/re-authentication path; HTTP 403 reports administrator authorization denial and does not log the user out.
- Unit tests cover fresh ticket creation, URL construction, bounded backoff, and the 4001/4003 policy mapping. They do not prove live backend behavior, ticket expiry timing, or active socket revocation.

All staging-dependent rows above remain **UNVERIFIED**. Local test/build evidence is not staging evidence.
