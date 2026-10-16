# Syx Hybrid Co-Op (v0.71.44)

[![Java 8 Bytecode Compatible](https://img.shields.io/badge/Java-1.8%20%2F%2021%20JRE-blue.svg)](https://openjdk.org/)
[![Songs of Syx](https://img.shields.io/badge/Songs%20of%20Syx-v0.71.44-orange.svg)](https://store.steampowered.com/app/1162750/Songs_of_Syx/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A unified multiplayer and co-op mod for **Songs of Syx** (`v0.71.44`), supporting two distinct gameplay modes:

- **Mode A: Shared-City Co-Op** — Deterministic lockstep simulation where two players rule a single settlement together in real time. Commands (room construction, designations, job priorities, speed toggles) are sequenced, timestamped, and applied deterministically across both clients.
- **Mode B: Shared-World Co-Op** — Asynchronous overworld sync where each player governs an independent empire on the same global map. Trade surpluses, population stats, diplomacy, and army movements are synchronized every 2 seconds. When two human armies clash, the mod seamlessly bridges into a live tactical battle!

---

## Features

- **In-Game Main Menu UI**: Host and Join directly from the start screen without needing command-line parameters.
- **Save Snapshot Transfer**: Automatic binary streaming of settlement saves (`SaveStreamer`) with CRC32 chunk validation.
- **Determinism Split**: Strict separation between simulation random sequences (`RND.rnd`) and UI random sequences (`RND.uiRnd`).
- **Tactical Battle Bridge**: When human-controlled factions clash on the overworld, players load into a live battle with AI disabled on player-controlled armies.
- **Cross-Platform LAN Support**: Play between Windows and macOS (Intel / Apple Silicon) over local Wi-Fi or virtual LAN (ZeroTier, Tailscale).
- **Persistent In-Game HUD & Overlay**: Monitor connection latency, simulation tick count, connected peers, and trigger forced save resyncs with **`F8`** or by clicking the top-bar pill.

---

## Quick Start (How to Play)

### Host a Game
1. Open Songs of Syx.
2. On the main menu, click **Multiplayer**.
3. Under **HOST SESSION** (left side):
   - Choose your mode (**Mode A** for one settlement, or **Mode B** for separate empires).
   - Keep default TCP Port (`27015`).
   - Click **Host from Save** (to load your existing world) or **Host New World**.

### Join a Game
1. On the client machine, open Songs of Syx.
2. Click **Multiplayer**.
3. Under **JOIN SESSION** (right side):
   - **Same PC (testing)**: Enter `127.0.0.1` and Port `27015`.
   - **Local Network (LAN / Wi-Fi)**: Enter the Host's local IP (e.g. `192.168.1.50`) and Port `27015`.
   - **Over Internet**: Enter Host's public IP or virtual LAN IP (ZeroTier / Tailscale).
4. Click **Connect & Join**. The game will download the host's save snapshot automatically and load into the session.

### In-Game Controls
- Press **`F8`** or click the top-right **`🟢 Co-Op`** pill at any time during gameplay to view peers, latency, and simulation ticks.
- If a desync ever occurs, the Host can click **"Force Save Resync"** to push a fresh snapshot to all connected clients in 2 seconds.

---

## Installation

### Manual Installation
1. Download `SyxHybridCoop.zip` from the [Latest Release](https://github.com/propst-design/SyxHybridCoop/releases).
2. Extract the `SyxHybridCoop` folder into your Songs of Syx mods directory:
   - **Windows**: `%APPDATA%\songsofsyx\mods\` (Paste into Explorer address bar)
   - **macOS**: `~/Library/Application Support/songsofsyx/mods/`
     > **Tip for Mac users**: macOS hides the `~/Library` folder by default.
     > - **Quickest Method**: Open **Terminal** and run:
     >   ```bash
     >   mkdir -p ~/Library/Application\ Support/songsofsyx/mods
     >   open ~/Library/Application\ Support/songsofsyx/mods
     >   ```
     > - **In Finder**: Press <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>G</kbd>, paste `~/Library/Application Support/songsofsyx/mods/`, and press <kbd>Return</kbd>.
   - **Linux**: `~/.local/share/songsofsyx/mods/`
3. Launch Songs of Syx, click the mod manager on the launcher, and ensure **Syx Hybrid Co-Op** is enabled.

---

## Building from Source

### Prerequisites
- JDK 21 (game runtime) with `-source 8 -target 1.8` bytecode compatibility
- Apache Maven 3.8+
- Songs of Syx (`v0.71.44`) installed on your system

### Build and Deploy
```bash
# Clone the repository
git clone https://github.com/propst-design/SyxHybridCoop.git
cd SyxHybridCoop

# Run test suite and install to local mod directory
mvn clean install
```

`mvn install` will compile the Java 1.8 bytecode, run all 12 headless JUnit tests, and stage the resulting `SyxHybridCoop.jar` directly to `%APPDATA%\songsofsyx\mods\SyxHybridCoop`.

---

## Local Dual-Instance Testing

To test multiplayer on a single PC without needing a second computer:
```bat
# Runs Host on port 27015 and Client connected to 127.0.0.1 with isolated save folder
launch-dual-test.bat
```
Logs are saved in `%APPDATA%\songsofsyx\logs\coop-host.log` and `coop-client.log`.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
All game assets, engine code, and vanilla class references remain the intellectual property of *Jake Versluis / Songs of Syx*.
