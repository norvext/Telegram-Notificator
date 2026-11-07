"""Check mention policy through Minecraft and the Telegram HTTP endpoint."""

from telegram_server import dedicated_server, expect_message, main, vanilla_player


def run_checks(directory, telegram, options):
    token = "123:integration_test"
    chat = -1001234567890

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
