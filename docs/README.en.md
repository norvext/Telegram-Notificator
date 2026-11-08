<p align="center">
  <img src="../.github/assets/TelegramNotificator-icon.png" alt="Telegram Notificator icon" width="128">
</p>

# Telegram Notificator

**Author** norvext · **Minecraft** 26.3 · **NeoForge** 26.3.0.101 · **Language** Java 25

[Документация на русском](../README.md)

The mod sends player join and server startup notifications to a Telegram group or topic. Players can link their Telegram usernames so the bot can mention them in notifications.

## Compatibility

The mod targets a dedicated Minecraft 26.3 server with NeoForge 26.3.0.101 and Java 25. Players can connect with a vanilla client.

## Setup

1. Put the mod JAR in `mods/` and start the dedicated server.
2. Create a bot with [@BotFather](https://t.me/BotFather). Add it to your group and grant it permission to send messages.
3. Run `telegram token set <token>` and `telegram chat set <chat_id> [topic_id]` in the server console. Use the group's negative ID. To send to a topic, add its numeric ID.
4. Run `telegram test` and check that the message appears in the intended group or topic.

The mod stores its settings and player links in `config/telegramnotificator-local.toml`. This file contains the bot token. Enter the token in a trusted console and remember that your hosting panel may retain command history.

## Notification templates

Open `config/telegramnotificator-local.toml`. The mod adds missing fields at startup. Set a distinct `serverName` for each server. Servers can share a bot token while keeping their own settings and player links.

````toml
serverName = "Survival EU"
notifyOnStartup = true

joinMessage = '''
🟢 **<PlayerName>** joined `<ServerName>`
<TgTags>
'''

startupMessage = '''
**<ServerName>** is ready.
Time to play! <TgTags>
'''

testMessage = 'Delivery check from `<ServerName>`'
````

- `<ServerName>` is available in every template. `<PlayerName>` is available in `joinMessage`.
- Place `<TgTags>` once in `joinMessage` or `startupMessage`, outside styled text. If there are no mentions, a line containing only `<TgTags>` is removed. If the list exceeds the message limit, the remaining mentions follow in separate messages.
- `**bold**`, `_italic_`, `__underline__`, and `~~strikethrough~~` select Telegram styles. Single backticks render inline monospace text, and triple backticks delimit a multiline block. Use styles individually. After substitution, the template without its mention list must fit in 4096 UTF-16 code units.

Run `telegram reload` after saving the file. A read or template validation error leaves the previous settings active. A test send checks `testMessage` and the current destination group or topic.

Startup notifications are enabled by default. The mod sends one when the server is ready and the bot and group are configured. It mentions all linked players on that server regardless of the `mentions` mode. A change to `notifyOnStartup` takes effect on the next startup.

## Commands

Use the leading `/` in game and omit it in the server console.

| Command | Access | Action |
| --- | --- | --- |
| `/telegram link <username>` | Player | Saves or replaces your Telegram link. Enter the username without `@`. |
| `/telegram unlink` | Player | Removes your link. |
| `/telegram unlink <username>` | Operator or console | Removes a link by Telegram username. |
| `/telegram token set <token>` | Operator or console | Saves the bot token. |
| `/telegram token remove` | Operator or console | Removes the token and pauses delivery. |
| `/telegram chat set <chat_id> [topic_id]` | Operator or console | Sets the destination group and topic. Omit `topic_id` to send to the main chat. |
| `/telegram mentions <all\|offline\|none>` | Operator or console | Mentions all linked players, only offline players, or nobody. The joining player is always excluded. Defaults to `all`. |
| `/telegram rejoin set <seconds>` | Operator or console | Skips the notification if a player rejoins before the interval elapses. Defaults to 300 seconds. Set it to `0` to disable the filter. |
| `/telegram test` | Operator or console | Sends a test message without mentions. |
| `/telegram reload` | Operator or console | Reloads settings and templates from the file. |
| `/telegram startup <true\|false>` | Operator or console | Enables or disables the notification on the next server startup. |
