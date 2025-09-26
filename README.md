# ServerLogs

ServerLogs is a small Minecraft server plugin that posts common server events to a configured Discord channel via JDA. It's designed to be useful for ops/admins who want a realtime feed of important activity (joins, chat, deaths, block changes, item pickups, etc.) while avoiding Discord spam.

## Features

- Posts events to a Discord text channel (requires a bot token).
- Locale support: messages are loaded from JSON files (built-in `en` and `da-dk`).
- Exports default locale files to `plugins/serverlogs/locale/` so admins can edit or add locales.
- Deduplication and anti-spam measures:
  - Short-cooldown dedupe for many event types (prevents immediate duplicates).
  - Region-based dedupe for block break/place (events within a ~25-block region are considered similar).
  - Spam detection: if the same event repeats frequently (configurable thresholds), the plugin will mute further identical events for a short period.
- Death inventory dump: when a player dies the plugin aggregates inventory/armor and posts a JSON array with item id/name/amount for easy parsing.
- Avoids logging creative-mode block edits to reduce noise.

## Installation

1. Build the plugin with Maven (or drop the provided jar) into your server `plugins/` folder.
2. Start the server to generate the default `config.yml` and the `locale` folder if they don't exist.
3. Edit `plugins/serverlogs/config.yml` and add your Discord bot token and channel id.
4. Reload or restart the server.

## Configuration

`config.yml` provides defaults. Important keys:

- `locale` - string. Example: `en` or `da`. The plugin will prefer files in `plugins/serverlogs/locale/` if present.
- `discord.token` - your Discord bot token.
- `discord.channel-id` - the Discord channel id to post into.
- `discord.activity` - what the bot shows as activity.

Example `config.yml`:

```yaml
locale: 'en'
discord:
  token: 'TOKEN'
  channel-id: 123456789012345678
  activity: ''
```

## Locales

- Default locales shipped: `en.json`, `da-dk.json`.
- On first run the plugin will copy these files into `plugins/serverlogs/locale/` so you can edit or add more locales.
- Locale files are simple JSON mapping keys to message templates. Placeholders use `{name}` syntax (e.g. `{player}`, `{coords}`, `{items}`).

## Commands

- `/serverlogs reload` - reloads the plugin configuration and reconnects to Discord. Requires `serverlogs.reload` permission (default: op).

## Behavior details

- Drop/Pickup logging includes item amount (e.g., `Diamond (3)`).
- Block break/place dedupe groups events in a 25-block region to reduce log spam during building or terraforming.
- Creative-mode players are ignored for block place/break logs.
- Death inventory is posted as a JSON array for easy parsing by bots or for copy/paste.
- Spam detection mutes a repeating event key if it occurs more than a threshold in a short window. Muted events are suppressed for a short mute period. Check console logs for mute notices.

## Troubleshooting

- If messages are not appearing, check server console for JDA connect errors and verify `discord.token` and `discord.channel-id` are correct.
- Ensure the bot has permission to send messages in the configured channel.


## Default locale

```
{
    "console.discord_connected": "Connected to Discord as {tag}",
    "console.discord_failed": "Failed to start JDA: {error}",
    "command.no_permission": "\u00a7cYou don't have permission to do that.",
    "command.usage": "\u00a7eUsage: /serverlogs reload",
    "command.reloading": "\u00a7eReloading serverlogs...",
    "command.reload_success": "\u00a7aserverlogs reloaded successfully.",
    "command.reload_fail": "\u00a7cFailed to restart Discord connection. Check console for details.",

    "event.command": "**{player}:** {cmd}",
    "event.death_killer": "**{victim}:** died to **{killer}**",
    "event.death": "**{victim}:** died",
    "event.death_items": "**{player}:** had items: {items}",
    "event.death_items_header": "**{player}:** inventory dump:",
    "event.drop": "**{player}** dropped **{item}**",
    "event.pickup": "**{player}** picked up **{item}**",
    "event.chat": "**{player}:** {msg}",
    "event.join": "**{player}** joined the server",
    "event.quit": "**{player}** left the server",
    "event.block_break": "**{player}** destroyed **{block}** at {coords}",
    "event.block_place": "**{player}** placed **{block}** at {coords}",
    "event.damage": "**{attacker}** hit **{victim}** with **{weapon}** for **{damage}** damage at {coords}",
    "event.teleport": "**{player}** teleported to {coords}",
    "event.world_change": "**{player}** switched to world **{world}**",
    "event.enchant": "**{player}** enchanted **{item}**",
    "event.portal": "**{player}** used a portal {coords}",
    "event.craft": "**{player}** crafted **{item}**"
}
```