"""Check mention policy through Minecraft and the Telegram HTTP endpoint."""

from queue import Empty
from uuid import UUID

from telegram_server import dedicated_server, expect_message, main, vanilla_player


def run_checks(directory, telegram, options):
    token = "123:integration_test"
    chat = -1001234567890

    overflow_dir = directory / "overflow"
    config_dir = overflow_dir / "config"
    config_dir.mkdir(parents=True)
    usernames = [f"user{index:028d}" for index in range(130)]
    bindings = ", ".join(f'"{UUID(int=index + 1)}:{username}"' for index, username in enumerate(usernames))
    (config_dir / "telegramnotificator-local.toml").write_text(
        f'bindings = [{bindings}, "{UUID(int=1000)}:{usernames[0].upper()}", '
        f'"{UUID(int=1)}:extra_username", "malformed", "{UUID(int=1001)}:invalid-name"]\n'
    )
    with dedicated_server(overflow_dir, telegram.server_port, options) as console:
        console.send(f"telegram token set {token}")
        console.wait_for("Токен сохранён")
        console.send(f"telegram chat set {chat}")
        console.wait_for("Группа уведомлений сохранена")
        with vanilla_player(console, "Alex"):
            header = "Alex вошёл на сервер Minecraft"
            expect_message(telegram, token, chat, header + "\n" + " ".join("@" + name for name in usernames[:119]))
            expect_message(telegram, token, chat, " ".join("@" + name for name in usernames[119:]))

        telegram.responses.put((403, b'{"ok":false}', 0))
        with vanilla_player(console, "Steve"):
            expect_message(telegram, token, chat, "Steve вошёл на сервер Minecraft\n"
                           + " ".join("@" + name for name in usernames[:119]))
            console.wait_for("Не удалось отправить сообщение Telegram: HTTP 403")
            try:
                unexpected = telegram.requests.get(timeout=1)
            except Empty:
                pass
            else:
                raise AssertionError(f"Unexpected Telegram request after first chunk failed: {unexpected!r}")

    with dedicated_server(directory, telegram.server_port, options) as console:
        console.send(f"telegram token set {token}")
        console.send(f"telegram chat set {chat}")
        with vanilla_player(console, "Alex") as (command, reply):
            expect_message(telegram, token, chat, "Alex вошёл на сервер Minecraft")
            command("telegram link alex_telegram")
            reply("Привязка сохранена")
            command("telegram mentions none")
            reply("command.context.here")

            with vanilla_player(console, "Steve") as (steve_command, steve_reply):
                expect_message(telegram, token, chat, "Steve вошёл на сервер Minecraft\n@alex_telegram")

                steve_command("telegram link steve_telegram")
                steve_reply("Привязка сохранена")

            console.wait_for("Steve left the game")
            console.send("telegram mentions none")
            console.wait_for("Режим упоминаний сохранён: none")
            with vanilla_player(console, "Charlie"):
                expect_message(telegram, token, chat, "Charlie вошёл на сервер Minecraft")

            console.send("op Alex")
            console.wait_for("Made Alex a server operator")
            command("telegram mentions offline")
            reply("Режим упоминаний сохранён: offline")
            with vanilla_player(console, "Dwight"):
                expect_message(telegram, token, chat, "Dwight вошёл на сервер Minecraft\n@steve_telegram")

    with dedicated_server(directory, telegram.server_port, options) as console:
        expect_message(telegram, token, chat, "Сервер Minecraft запущен\n@alex_telegram @steve_telegram")
        with vanilla_player(console, "Donna") as (command, reply):
            expect_message(telegram, token, chat, "Donna вошёл на сервер Minecraft\n@alex_telegram @steve_telegram")
            command("telegram link donna_telegram")
            reply("Привязка сохранена")
            with vanilla_player(console, "Erin"):
                expect_message(telegram, token, chat, "Erin вошёл на сервер Minecraft\n@alex_telegram @steve_telegram")

            console.send("telegram mentions all")
            console.wait_for("Режим упоминаний сохранён: all")
            with vanilla_player(console, "Frank"):
                expect_message(telegram, token, chat, "Frank вошёл на сервер Minecraft\n@alex_telegram @steve_telegram @donna_telegram")


if __name__ == "__main__":
    main(run_checks)
