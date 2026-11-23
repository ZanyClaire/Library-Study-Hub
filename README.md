# Library Study Hub — Java LAN Chat Application (ITAS1 Final Project)

## 1. Project Overview
**Library Study Hub** is a multi-client Java TCP socket application built for small library study groups collaborating over a local area network (LAN). In addition to real-time group chat, online user tracking, and command handling (`/name`, `/exit`, `/help`), it provides two synchronized study features managed by the server in memory:
1. **Per-Student Study Timers (Requires Approved Seat)** — Once a student has an approved study seat, they can start their own independent countdown duration and study topic (`My Study Timer`), with `Start`, `Pause`, `Resume`, and `Reset` controls. Students without an approved seat cannot start a timer, and releasing a seat automatically resets any active timer. The server ticks every second and broadcasts everyone's live progress to the **Student Timers** sidebar on all connected clients.
2. **Study Seat Request, Librarian Approval & Monitoring Console** — An interactive seat map where students request free seats (`FREE` -> `PENDING`) or release their own seat, while the **Librarian Server Console (`ServerFrame`)** monitors live student conversations, sends official Librarian announcements to all students, tracks every student's live seat and timer status in the **Student Monitor** table, and approves (`TAKEN`), denies (`FREE`), or releases seats. All changes synchronize instantly across all connected clients, and disconnecting automatically frees a student's seat.

Both the Server and Client GUIs are built with **NetBeans Swing GUI Builder (`.form` drag-and-drop files)**, use **in-memory data only (no database)**, and load all default settings and theme colors from [`config.properties`](src/main/resources/librarystudyhub/config.properties) (or an optional external `studyhub.properties` file) so no configuration values are hard-coded in the Java source.

---

## 2. Team Members & Course Information
| Role | Name | GitHub Username | Course & Section |
| :--- | :--- | :--- | :--- |
| Partner 1 | *(Fill in Name 1)* | `@username1` | ITAS1 — *(Section)* |
| Partner 2 | *(Fill in Name 2)* | `@username2` | ITAS1 — *(Section)* |

### Contribution Log
| Date | Member | Task Description | Commit Link |
| :--- | :--- | :--- | :--- |
| 2026-11-10 | Partner 1 | Protocol design, `ChatServer`, `ClientHandler`, `Seat`, and Librarian Server GUI (`ServerFrame.form`) | *(link)* |
| 2026-11-10 | Partner 2 | Client networking (`ClientConnection`), client GUI (`ClientFrame.form`), per-student timers & seats UI, testing | *(link)* |

---

## 3. Concept Proposal & Instructor Approval
- **Target Audience:** Small library study groups (2–10 students) reviewing together in a library room or campus Wi-Fi zone.
- **User Problem:** Ordinary chat apps do not let students track individual study countdowns side-by-side or coordinate limited study table seats with librarian approval.
- **Original Feature 1 — Independent Per-Student Study Timers:** Every student can start their own focus timer with a custom duration and topic (e.g., 15 minutes for `History`, 25 minutes for `Math Review`). The server ticks all active timers once per second and broadcasts `TIMERS` updates so every client sees each classmate's live countdown in **Student Timers**.
- **Original Feature 2 — Study Seat Request & Librarian Approval:** The server maintains the library seat table. Students click a free seat and click **Request Seat** (`PENDING`). On the **Librarian Server Console**, the librarian sees pending seat requests and clicks **Approve** (`TAKEN`), **Deny** (`FREE`), or **Release** (`FREE`). All clients immediately receive the updated `SEATS` map, and disconnecting automatically frees any held seat.
- **Instructor Approval Status:** *(Approved / Date / Instructor Initials)*

---

## 4. Architecture & Message Format

```
+-------------------+          TCP Socket (UTF-8 lines)          +---------------------------+
|   ClientFrame     |  <-------------------------------------->  |    ChatServer             |
| (Swing EDT +      |   HELLO / CHAT / CMD / TIMER_* / SEAT_*    | (ServerSocket +           |
|  ClientConnection)|                                            |  ClientHandler per client |
+-------------------+                                            |  + 1s Timer Ticker +      |
                                                                 |  Librarian ServerFrame)   |
                                                                 +---------------------------+
```

### Wire Protocol (`common.Protocol`)
Every message is a single UTF-8 line terminated by `\n`:
```
TYPE|field1|field2|...
```
Special characters inside fields are escaped (`\` -> `\\`, `|` -> `\p`, `\n` -> `\n`, `\r` -> `\r`) so user chat text can never corrupt a frame.

#### Client -> Server Messages
| Type | Format | Description |
| :--- | :--- | :--- |
| `HELLO` | `HELLO\|nickname` | Initial handshake sent immediately after TCP connect |
| `CHAT` | `CHAT\|text` | Broadcast a chat message to the group |
| `CMD` | `CMD\|/name <newname>` \| `/exit` \| `/help` | Execute a slash command validated by the server |
| `TIMER_START` | `TIMER_START\|minutes\|label` | Start this student's own study timer |
| `TIMER_PAUSE` | `TIMER_PAUSE` | Pause this student's running timer |
| `TIMER_RESUME` | `TIMER_RESUME` | Resume this student's paused timer |
| `TIMER_RESET` | `TIMER_RESET` | Reset this student's timer back to `IDLE` |
| `SEAT_REQUEST` | `SEAT_REQUEST\|seatNo` | Request a free study seat (1-based index) |
| `SEAT_RELEASE` | `SEAT_RELEASE\|seatNo` | Release the student's own `PENDING`/`TAKEN` seat |

#### Server -> Client Messages
| Type | Format | Description |
| :--- | :--- | :--- |
| `WELCOME` | `WELCOME\|nickname` | Handshake accepted |
| `REJECT` | `REJECT\|reason` | Handshake rejected (invalid or duplicate nickname) |
| `NICK` | `NICK\|newNickname` | Confirms the caller's `/name` change succeeded |
| `CHAT` | `CHAT\|HH:mm:ss\|sender\|text` | Group chat message broadcast once to all clients |
| `SYSTEM` | `SYSTEM\|HH:mm:ss\|text` | Join, leave, rename, timer, and seat event notices |
| `INFO` | `INFO\|text` | Private command output (e.g., `/help`) |
| `ERROR` | `ERROR\|text` | Validation error returned to the requesting client |
| `USERS` | `USERS\|nick1\|nick2\|...` | Full online students list |
| `TIMERS` | `TIMERS\|nick1\|state1\|rem1\|tot1\|label1\|...` | Synchronized per-student timer snapshots |
| `SEATS` | `SEATS\|count\|status1\|holder1\|status2\|holder2\|...` | Synchronized seat table snapshot |
| `BYE` | `BYE\|reason` | Clean disconnect or server shutdown notice |

---

## 5. Setup, Drag-and-Drop GUI Editing & Running Instructions

### Requirements
- **Java Version:** JDK 17 or newer (tested on JDK 26). No external libraries or databases required.
- **IDE (for Drag-and-Drop GUI Builder):** Apache NetBeans (open the project folder directly; double-click `ServerFrame.java` or `ClientFrame.java` and switch to the **Design** tab to drag and drop Swing components).
  - After pulling updates, right-click the project in NetBeans → **Clean and Build** before running.

### Option A — Quick Start on Windows (Batch Scripts)
1. Double-click **`run-server.bat`** and click **Start Server**.
2. Double-click **`run-client.bat`** three times to open three client windows.
3. Enter the Server IP (`localhost` on the same PC, or the LAN IP shown in the server log when connecting from another computer on the same Wi-Fi/LAN), port (`5050`), and a unique nickname, then click **Connect**.

### Option B — Command Line (`javac` / `java`)
```powershell
mkdir target\classes -Force
javac -encoding UTF-8 -d target\classes src\main\java\common\*.java src\main\java\server\*.java src\main\java\client\*.java
Copy-Item -Recurse -Force src\main\resources\* target\classes\

# Start Librarian Server Console:
java -cp target\classes server.ServerFrame

# Start Student Client (run in 3 terminals / across 2 LAN machines):
java -cp target\classes client.ClientFrame
```

### Troubleshooting LAN Connections
- Look at the first line of the **Server Activity Log** in `ServerFrame`—it prints the machine's active LAN IPv4 address(es) (e.g. `192.168.1.x`).
- If a second laptop cannot connect, allow Java through Windows Defender Firewall on TCP port `5050` (or change `server.port` in `studyhub.properties`).

---

## 6. Supported Commands
| Command | Example | Behavior |
| :--- | :--- | :--- |
| `/name <newname>` | `/name Anna_2` | Validates uniqueness and format (`2-16` letters, digits, or `_`), updates online list, timers, seat holder tags, and notifies all clients |
| `/help` | `/help` | Displays available commands and study hub feature instructions |
| `/exit` | `/exit` | Cleanly disconnects from the server, releases any held seat, and updates remaining clients |

---

## 7. Labeled Application Screenshots

### Librarian Server Console (`ServerFrame`)
![Librarian Server Console](screenshots/server.png)

### Student Desktop Workspace (`ClientFrame`)
![Student Client Workspace](screenshots/client-anna.png)

---

## 8. Minimum Test Cases & Results
| # | Test Case | Steps | Expected Result | Actual Result | Status |
| :- | :--- | :--- | :--- | :--- | :--- |
| 1 | Three concurrent clients | Connect `alice`, `bob`, and `cara` to the server | All 3 connect; online list shows `alice`, `bob`, `cara` on every client and server | All 3 connected and listed | **PASS** |
| 2 | Group chat broadcast | `bob` sends a chat message | Message appears once on `alice`, `bob`, and `cara` with timestamp and sender | Broadcast received once by all 3 | **PASS** |
| 3 | Duplicate / invalid nickname | Connect a 4th client as `Bob` or blank; or run `/name alice` | Server rejects duplicate (case-insensitive) and blank nicknames with clear error | Rejected with error message | **PASS** |
| 4 | Valid `/name` change | `bob` sends `/name bobby` | Nickname updates in chat, online list, timers, and any held seat across all clients | Updated everywhere | **PASS** |
| 5 | Blank message rejection | Click **Send Message** with empty/whitespace input | Client & server reject blank message and show warning | Rejected with error notice | **PASS** |
| 6 | Invalid IP / port / unavailable server | Enter port `99999` or connect when server is stopped | GUI stays responsive; friendly error dialog explains the failure | Error dialog shown, GUI responsive | **PASS** |
| 7 | Clean `/exit` | Send `/exit` from `bobby` | Client disconnects cleanly; seat is freed and online list updates for remaining clients | Disconnected and state updated | **PASS** |
| 8 | Abrupt client disconnection | Close `alice`'s socket/window abruptly | Server detects EOF, frees `alice`'s seat & timer, and chat continues between remaining clients | Handled cleanly, chat continues | **PASS** |
| 9 | Original Feature 1: Per-Student Study Timers | Student 1 starts a 15-min timer (`History`); Student 2 starts a 25-min timer (`Math Review`); Student 1 pauses/resets their own | Each student has an independent countdown while **Student Timers** syncs everyone's live progress across all clients | Independent timers synced across all clients | **PASS** |
| 10 | Original Feature 2: Study Seat Request & Librarian Approval | Student 1 requests Seat 1 (`PENDING`); Librarian approves (`TAKEN`) on `ServerFrame`; Student 2 requests Seat 3 (`PENDING`); Librarian denies (`FREE`); Student 1 releases (`FREE`) | Seat colors, holder names, and pending counts synchronize across all clients and the server | Synchronized across all clients & server | **PASS** |
