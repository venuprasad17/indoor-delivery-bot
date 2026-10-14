# Robot Operator - Native Mobile Operations App

Internal operations mobile application for the Autonomous Indoor Delivery Robot backend (FastAPI + JWT + WebSocket).

## Operations Design Principles
- **No Demoware Aesthetic**: Strictly light-themed, utilitarian industrial operations UI.
- **Color Semantics**: 
  - Green = Online / Safe / Tracking / Active
  - Amber = Paused / Slow / Relocating / Manual
  - Red = Stop / Lost / Emergency Stop / Fault
  - Grey = Offline / Idle / Finished
- **Always Reachable E-STOP**: Pinned red emergency stop in the persistent header strip that fires immediately with zero confirmation prompt.
- **Hardware Telemetry & Mapping**: Real-time 2D pose trail canvas plotting with grid crosshairs and heading direction indicator.

## Configuration & Network Setup
1. **Server Settings**:
   - First launch prompts for server address (default: `http://10.0.2.2:8000` for Android emulator or your computer's LAN IP e.g. `http://192.168.1.x:8000`).
   - "Test connection" verifies `GET /health` (`{"status":"ok"}`).
   - Automatically derives WebSocket URL: `http://` ➔ `ws://`, `https://` ➔ `wss://`.
   - Cleartext traffic is enabled in Android Manifest for local operational networks.

2. **Authentication**:
   - Admin-only access. Non-admin roles receive an explicit notice and cannot access robot controls.
   - JWT access tokens stored securely in memory with automated HTTP-only cookie refresh interception on 401.

3. **Safety Simulation Mode**:
   - When physical robot hardware is offline or local testing is required, the app includes an embedded operations simulation engine with live 2D telemetry, active mapping, destinations, and delivery missions.
