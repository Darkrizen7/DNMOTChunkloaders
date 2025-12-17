# DChunkLoader (Forge)

**DChunkLoader** is a lightweight, server-side chunk loading solution. It allows players to keep specific areas active
without complex menus, while ensuring server performance by linking chunk activity to player or team presence.

## 🌟 Key Features

* **Server-Side Only**: No client installation required. Players can join with a vanilla client.
* **Team Integration**: Your chunks stay loaded as long as **you** or **at least one member of your team** is online.
* **Auto-Hibernation**: As soon as the entire team logs off, the chunk loaders deactivate to save server resources (
  TPS).
* **Auto-Cleanup**: If a player remains inactive (no login) for a configurable number of days, their chunk loaders are
  automatically deleted to prevent world clutter.

---

## 🎮 How it Works

1. Place the **Activation Block** (Default: Iron Block) in the center of the chunk you want to load.
2. Right-click the block with the **Activation Item** (Default: Stick).
3. The chunk is now loaded as long as you or a teammate is on the server!

---

## ⌨️ Commands

DChunkLoader uses a modular command system under the `/dcl` base. Clickable text in the chat allows for quick actions
like teleporting to loaders or joining teams.

### 👤 Player Commands

| Command                  | Description                                               |
|:-------------------------|:----------------------------------------------------------|
| `/dcl chunkloader list`  | Check your current loader count against the server limit. |
| `/dcl chunkloader clean` | Remove all your personal chunk loaders at once.           |
| `/dcl teams info`        | View your team members and their online status.           |
| `/dcl teams leave`       | Leave your current team.                                  |

### 🤝 Team Management

| Command                      | Description                                         |
|:-----------------------------|:----------------------------------------------------|
| `/dcl teams create <name>`   | Start a new team.                                   |
| `/dcl teams invite <player>` | Invite a friend to your team.                       |
| `/dcl teams join <name>`     | Accept an invitation (or click the invite message). |

### 🛠 Admin Commands (Permission Level 2)

| Command                             | Description                                                                     |
|:------------------------------------|:--------------------------------------------------------------------------------|
| `/dcl chunkloader list actives`     | View a global breakdown of active vs total loaders per dimension.               |
| `/dcl chunkloader list <player>`    | Inspect and list all loaders belonging to a specific player.                    |
| `/dcl chunkloader list team <name>` | List all loaders belonging to a team with teleport links.                       |
| `/dcl chunkloader debug`            | Scan the server for inconsistencies between mod data and Forge's forced chunks. |
| `/dcl chunkloader debug clean`      | Forcefully unforce "rogue" chunks not managed by DCL.                           |
| `/dcl chunkloader clean <player>`   | Remotely delete all loaders belonging to a specific player.                     |
| `/dcl teams list`                   | List every team registered on the server.                                       |

---

## ⚙️ Configuration

You can customize the mod behavior in the `config/dchunkloader-server.toml` file:

| Option                  | Default                | Description                                                   |
|:------------------------|:-----------------------|:--------------------------------------------------------------|
| `activationBlock`       | `minecraft:iron_block` | The block type used as a chunk loader.                        |
| `activationItem`        | `minecraft:stick`      | The item used to toggle the loader.                           |
| `maxLoadersPerPlayer`   | `4`                    | Max number of loaders allowed per player (Range: 2 - 100).    |
| `maxChunkloaderAgeDays` | `4`                    | Days of inactivity before automatic removal (Range: 2 - 100). |

---

## 📦 Requirements & Installation

* **Forge**: Version `47.x` or higher.
* **Installation**: Place the `.jar` file into your server's `mods` folder and restart.

---

**Note**: This mod is designed for server administrators who want a fair and performance-friendly chunk loading system
for their community.