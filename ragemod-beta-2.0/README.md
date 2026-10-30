# RAGE Mod, Beta 2.0 (Fabric 1.21.11)

Client-side Fabric mod for the RAGE clan.

## What's new in 2.0
- **Groups with emblems:** Target (crossed swords), Friend (heart), **Alliance (RAGE skull)**, **Mutual** (linked rings),
  Annoyance (warning sign), Enemy (war cross), and a star for the **top priority target**
- **Join / leave alerts** in chat when a target (and optionally friends, alliances, mutuals, enemies, top target) joins or leaves the lobby
- **Player Info tab:** search any player, player card with the skin they're wearing, your K/D against them, name history,
  save their skin file, and everything they said with the time
- **Top priority target** for wars (synced to the clan, shown on the HUD and in nick/hitbox color)
- **Vanilla-style buttons**, more colorful panels, tracers removed
- **Chat inside the mod** (Mod Users tab), no website for players, the relay address is built in
- **Dev tab** (side button on the main menu): password login, all mod users with IP, pending names, allow / remove / kick
- **Access by Minecraft name**, managed from the Dev tab

## Build the jar
Push this project to GitHub. The workflow in `.github/workflows/build.yml` builds it.
Actions tab -> latest run -> Artifacts -> use `ragemod-2.0.0-beta.jar`.
Needs Fabric Loader 0.19+ and Fabric API for 1.21.11.

## Relay setup (Render)
The relay in `relay/` decides who may use the mod and carries chat, help, war and admin messages.
Set these **environment variables** on the Render service (Environment tab):

| Name | Value |
|---|---|
| `ADMIN_PASSWORD` | the Dev tab password (it is checked by the relay only, never stored in the mod) |
| `OWNER_NAMES` | your Minecraft name(s), comma separated. Always allowed. |
| `GITHUB_TOKEN` | (recommended) token so the allowlist survives restarts, see below |
| `GITHUB_REPO` | (recommended) `owner/repo` of a repo to store `allowlist.json` in (private is fine) |

Without the GitHub variables the allowlist is only kept in memory/local disk. Render's free instances lose local files when
they restart, so anyone you allowed from the Dev tab would be forgotten.

### GitHub token for the allowlist
1. Make a repo, e.g. `rage-allowlist` (private is fine).
2. GitHub -> Settings -> Developer settings -> Personal access tokens -> Fine-grained tokens -> Generate new token.
3. Repository access: only `rage-allowlist`. Permissions: **Contents: Read and write**.
4. Put the token in Render as `GITHUB_TOKEN` and `3iden-del/rage-allowlist` as `GITHUB_REPO`.

## Dev tab
Open the menu with Right Shift, click **DEV** on the right side, enter the password.
- **Users:** everyone connected, with IP and how long they've been online (Kick)
- **Pending:** names that tried to use the mod and are locked (Allow)
- **Allowlist:** everyone allowed (Remove), plus a box to add a name

IP addresses are private data. Don't share screenshots of the Dev tab.

## Notes
- Access is checked by **name**. Names can be faked by someone who edits the mod, so this stops casual sharing, not a determined person.
- The relay sees each user's name, server and IP. The mod tells users this once.
- Player Info uses Mojang (UUID, skin) and laby.net (name history). Turn it off with `lookupNameHistory` in the config.
- Kill tracking reads chat lines like `X was slain by Y`.
- Data is saved in `.minecraft/config/ragemod/` (statuses, kills, chat logs, skins).
