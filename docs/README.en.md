<div align="center">
  <br>
  <img src="../.github/assets/TelegramNotificator-icon.png" alt="Telegram Notificator icon" width="256">
  <h1>Telegram Notificator</h1>
  <p><strong>Mod version</strong> 1.2 · <strong>Author</strong> norvext · <strong>Minecraft</strong> 1.21.1 · <strong>NeoForge</strong> 21.1.x · <strong>Language</strong> Java 21</p>
  <p><a href="../README.md">Документация на русском</a></p>
  <p>The mod sends player join, player leave and server startup notifications to a Telegram group or topic. Players can link their Telegram usernames so the bot can mention them in notifications.</p>
</div>

## Compatibility

The mod targets a dedicated Minecraft 1.21.1 server with NeoForge 21.1.x (built and checked against 21.1.265) and Java 21. It is server-only: players do not need it, and it does not load on a client or in single-player.

## Setup

1. Put the mod JAR in `mods/` and start the dedicated server.
2. Create a bot with [@BotFather](https://t.me/BotFather). Add it to your group and grant it permission to send messages.
3. Run `telegram token set <token>` and `telegram chat set <chat_id> [topic_id]` in the server console. Use the group's negative ID. To send to a topic, add its numeric ID.
4. Run `telegram test` and check that the message appears in the intended group or topic. A failure reply includes the reason given by Telegram, such as `chat not found`.
5. `telegram status` shows what is already configured, and `telegram` without arguments lists the available commands.

The mod stores its settings and player links in `config/telegramnotificator-local.toml`. On first start it creates this file with every field and a comment for each (comments are in Russian). This file contains the bot token. Enter the token in a trusted console and remember that your hosting panel may retain command history.

## Notification templates

Open `config/telegramnotificator-local.toml`. The mod adds missing fields at startup. Set a distinct `serverName` for each server. Servers can share a bot token while keeping their own settings and player links.

````toml
serverName = "Survival EU"
notifyOnStartup = true
notifyOnLeave = true

joinMessage = '''
🟢 **<PlayerName>** joined `<ServerName>` (<Online>/<MaxPlayers>)
<TgTags>
'''

leaveMessage = '🔴 **<PlayerName>** left, <Online> still playing'

startupMessage = '''
**<ServerName>** is ready.
Time to play! <TgTags>
'''

testMessage = 'Delivery check from `<ServerName>`'
````

- `<ServerName>`, `<Online>` (players in game) and `<MaxPlayers>` (player slots) are available in every template. `<PlayerName>` is available in `joinMessage` and `leaveMessage`.
- Place `<TgTags>` once in `joinMessage` or `startupMessage`, outside styled text. If there are no mentions, a line containing only `<TgTags>` is removed. If the list exceeds the message limit, the remaining mentions follow in separate messages.
- `**bold**`, `_italic_`, `__underline__`, and `~~strikethrough~~` select Telegram styles. Single backticks render inline monospace text, and triple backticks delimit a multiline block. Use styles individually. After substitution, the template without its mention list must fit in 4096 UTF-16 code units.

Run `telegram reload` after saving the file. A read or template validation error leaves the previous settings active. A test send checks `testMessage` and the current destination group or topic.

The file can be edited on Windows: CRLF line endings do not leak into messages.

Startup notifications are enabled by default. The mod sends one when the server is ready and the bot and group are configured. It mentions all linked players on that server regardless of the `mentions` mode. A change to `notifyOnStartup` takes effect on the next startup.

Leave notifications are disabled by default. One is sent without mentions once the `rejoinSeconds` interval has passed after a player left and the player has not returned. A quick rejoin produces neither a leave nor a join message. No leave messages are sent when the server stops.

## Commands

Use the leading `/` in game and omit it in the server console.

| Command | Access | Action |
| --- | --- | --- |
| `/telegram` | Everyone | Shows your link and the commands available to you. |
| `/telegram status` | Operator or console | Shows the current settings without the token. |
| `/telegram list` | Operator or console | Shows player links. |
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
| `/telegram leave <true\|false>` | Operator or console | Enables or disables player leave notifications. |

## Building and checking

JDK 21 is required. `./gradlew build` produces `build/libs/telegramnotificator-1.21.1-<version>.jar` and runs the unit tests for the pure logic in the `core` package. The scenarios in `tests/` start a dedicated server with a Telegram stub and connect a player over the 1.21.1 protocol, for example `python tests/telegram_server.py`. They run on Linux and Windows.
