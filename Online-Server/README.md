# Online Server - WebSocket & Attendance System

This server handles WebSocket connections for real-time player tracking in the multiplayer map, and includes a REST API with an attendance system for managing class attendance.

## Features

- **WebSocket Server**: Real-time player position tracking, map synchronization, and disconnect handling.
- **Zombie Game Logic**: Mini-game with zombie AI routing and collision detection matrices.
- **Attendance System**: Record and query student attendance with built-in duplicate prevention.

## Prerequisites

Before starting, make sure you have the following installed on your machine:
- Node.js (v14 or higher)
- Docker and Docker Desktop (Must be open and running in the background)
- npm or yarn

## Setup Instructions

### 1. Install Dependencies
Open your terminal inside the `Online-Server` folder and run:

```bash
npm install
```

### 2. Environment Variables (.env)
You must create a `.env` file in the root of the `Online-Server` folder.

Important note for Windows users: using `127.0.0.1` instead of `localhost` is highly recommended to avoid Prisma authentication errors (Error P1000).

Create a file named exactly `.env` and paste the following inside (adjust the port if your local Postgres uses a different port):

```text
DATABASE_URL="postgresql://attendance_user:attendance_pass@127.0.0.1:5433/attendance_db?schema=public"
PORT=3000
```

> Note: Port `5433` is used here based on your local Postgres configuration. Change it if your DB uses a different port.

### 3. Start the Database
Ensure Docker Desktop is open, then start the PostgreSQL database using Docker Compose:

```bash
docker-compose up -d
```

### 4. Initialize Prisma Database
Once the database is running, generate the Prisma Client and run the migrations to create all the necessary tables:

```bash
npx prisma generate
npx prisma migrate dev --name init
```

### 5. Start the Server

```bash
node server.js
```

The server will start and listen on http://localhost:3000.

## Connecting the Android App (Troubleshooting)

If your game/app is not connecting to the server, verify the following in your Kotlin code (e.g., `ServerConnectionManager.kt`) and your network environment:

- Android Emulator: Use `ws://10.0.2.2:3000` (this is the emulator's special alias for your PC's localhost).
- Physical Android Device: Both your PC and phone must be on the exact same Wi‑Fi network. Use your PC's local IPv4 address (e.g., `ws://192.168.1.75:3000`).
- Ensure your Windows Firewall is temporarily turned off for private networks, or create an inbound rule allowing TCP traffic on port `3000`.
- Cleartext traffic: Ensure your Android app allows non-HTTPS connections. In `AndroidManifest.xml` include `android:usesCleartextTraffic="true"` inside the `<application>` tag and add the permission `<uses-permission android:name="android.permission.INTERNET" />`.

## Attendance API

### Register Attendance

**Endpoint:** `POST /attendance`

**Request Body:**

```json
{
  "phoneID": "unique-phone-identifier-string",
  "fullName": "John Doe",
  "group": "Group A"
}
```

Notes:
1. **Duplicate Prevention:** A student (identified by `phoneID`) can only be registered once per class (group + date).
2. **Timestamp:** The `attendanceTime` is automatically set to the current date and time on the server.
3. **Group Filtering:** Attendance can be queried by date and group inside the database.

## Project Structure

```
Online-Server/
├── prisma/
│   ├── schema.prisma          # Database schema and models
│   └── migrations/            # Auto-generated DB migration history
├── server.js                  # Main Express & WebSocket server file
├── zombieController.js        # Zombie enemy game logic
├── collisionMatrices.js       # Collision detection for map matrices
├── docker-compose.yml         # Database container configuration
├── package.json               # Node.js Dependencies
├── .env                       # Environment variables (You create this)
└── README.md
```
