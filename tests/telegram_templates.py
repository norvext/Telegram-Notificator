"""Check custom messages, startup delivery, and hot reload on a dedicated server."""

import json

from telegram_server import dedicated_server, expect_message, main, vanilla_player

TOKEN = "123:template_test"
CHAT = -1001234567890
NAME = "Realm _One_ <PlayerName> & $5"
BINDING = "00000000-0000-0000-0000-000000000001:alex_telegram"


def config_text(name, policy):
    return f"""# Server notifications
token = "{TOKEN}"
chatId = {CHAT}
serverName = {json.dumps(name)}
bindings = ["{BINDING}"]
mentionPolicy = "{policy}"
startupMessage = '''
🧭 **<ServerName>**
Пора играть (<TgTags>)
'''
joinMessage = '''
**<PlayerName>** на `<ServerName>`
Привет<TgTags>игрокам
_Готовы?_
'''
testMessage = '''
__Проверка__ ~~тест~~
```
<ServerName>
```
'''
"""


def expect_styled(telegram, text, *styles):
    entities = []
    for kind, substring in styles:
        index = text.index(substring)
        entities.append({
            "type": kind,
            "offset": len(text[:index].encode("utf-16-le")) // 2,
            "length": len(substring.encode("utf-16-le")) // 2,
        })
    assert telegram.requests.get(timeout=5) == (
        f"/bot{TOKEN}/sendMessage",
        {"chat_id": CHAT, "text": text, "entities": entities},
    )


def run_checks(directory, telegram, options):
    config = directory / "config" / "telegramnotificator-local.toml"
    config.parent.mkdir()
    # The first file imitates one edited on Windows: CRLF must not leak into messages.
    config.write_text(config_text(NAME, "none"), encoding="utf-8", newline="\r\n")

    with dedicated_server(directory, telegram.server_port, options) as console:
        expect_styled(telegram, f"🧭 {NAME}\nПора играть (@alex_telegram)", ("bold", NAME))
        sent = "Сообщение Telegram отправлено (запуск сервера)"
        if not any(sent in line for line in console.transcript):
            console.wait_for(sent)
        migrated = config.read_text(encoding="utf-8")
        assert "notifyOnStartup = true" in migrated
        assert "# Server notifications" in migrated
        assert 'joinMessage = """' in migrated

        with vanilla_player(console, "Steve") as (command, reply):
            expect_styled(telegram, f"Steve на {NAME}\nПривет игрокам\nГотовы?",
                          ("bold", "Steve"), ("code", NAME), ("italic", "Готовы?"))
            command("telegram reload")
            reply("command.context.here")
            command("telegram startup false")
            reply("command.context.here")

        config.write_text(config_text("Second server", "all"), encoding="utf-8", newline="\n")
        console.send("telegram startup false")
        console.wait_for("Не удалось сохранить настройки Telegram. Проверьте файл")
        assert 'serverName = "Second server"' in config.read_text(encoding="utf-8")
        console.send("telegram reload")
        console.wait_for("Настройки Telegram перечитаны")

        with vanilla_player(console, "Robin"):
            expect_styled(telegram, "Robin на Second server\nПривет @alex_telegram игрокам\nГотовы?",
                          ("bold", "Robin"), ("code", "Second server"), ("italic", "Готовы?"))

        valid = config.read_text(encoding="utf-8")
        config.write_text(valid.replace("notifyOnStartup = true", 'notifyOnStartup = "false"'), encoding="utf-8", newline="\n")
        console.send("telegram reload")
        console.wait_for("Настройки Telegram не обновлены: notifyOnStartup")
        config.write_text("serverName = '''unfinished", encoding="utf-8", newline="\n")
        console.send("telegram reload")
        console.wait_for("Не удалось прочитать настройки Telegram")
        console.send("telegram test")
        expect_styled(telegram, "Проверка тест\nSecond server",
                      ("underline", "Проверка"), ("strikethrough", "тест"), ("pre", "Second server"))
        console.wait_for("Пробное сообщение отправлено")

        config.write_text(valid, encoding="utf-8", newline="\n")
        console.send("telegram startup false")
        console.wait_for("Уведомление о запуске выключено")
        assert "notifyOnStartup = false" in config.read_text(encoding="utf-8")

    with dedicated_server(directory, telegram.server_port, options) as console:
        console.send("telegram test")
        expect_styled(telegram, "Проверка тест\nSecond server",
                      ("underline", "Проверка"), ("strikethrough", "тест"), ("pre", "Second server"))
        console.wait_for("Пробное сообщение отправлено")
        assert telegram.requests.empty()


if __name__ == "__main__":
    main(run_checks)
