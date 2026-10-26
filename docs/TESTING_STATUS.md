# Delivery update and device testing status

## Changes

- Mapped pickup/source and destination selection, route names in lists/details,
  source arrival and package-loading confirmation.
- Robot selection follows the chosen map, with same-map route validation.
- Long-running delivery calls use a longer timeout.
- Granted robot access refreshes when opening More.
- Dashboard reconnection state, camera certificate validation, and demo data view.
- Access-dialog layout fix keeps the Revoke action visible beside long robot IDs.
- Connection errors request retry; a failed fetch is not presented as an empty account.

The backend must be upgraded together with this frontend: new booking requests
require source_id and location lists supply robot_id. Apply backend migration
72c1a5e9d031 before using the updated API.

## Verification and checkpoint

45 Android tests passed before the last device walkthrough fixes. The walkthrough
exercised both logins, admin tools, mapping details, destinations, audit events,
inventory/grants, settings, console, registration validation, and real API bookings
through the installed app. Receipt confirmation was found to depend incorrectly
on navigation and was fixed in the backend and verified on the device.

One additional regression test covers honest retry wording (46 total tests).
The last full test/build rerun was interrupted when testing was paused at the
operator's request. A preceding run used the old compiled message while the new
test was added and reported that assertion failure. Do not treat the final source
as fully verified until rerunning the test task with Java 21.

Pending: final rebuild/install, on-device Revoke-button and offline-error-state
retests, and remaining creation-form checks. No updated APK was installed for these
last UI fixes. Ordinary screenshot test execution is not full-screen visual QA.

Hardware navigation, live telemetry, physical E-stop execution, and live camera
video remain unverified. The configured camera server refused its connection.

Run with JDK 21 from this repository:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```
