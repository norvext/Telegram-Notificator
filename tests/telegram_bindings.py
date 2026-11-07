"""Check player bindings through Minecraft and the Telegram HTTP endpoint."""

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
            command("telegram link alex_updated")
            reply("Привязка сохранена")

        console.wait_for("Alex left the game")
        with vanilla_player(console, "Steve") as (command, reply):
            expect_message(telegram, token, chat, "Steve вошёл на сервер Minecraft\n@alex_updated")
            command("telegram link steve_telegram")
            reply("Привязка сохранена")
            command("telegram link ALEX_UPDATED")
            reply("Этот Telegram username уже привязан")

            with vanilla_player(console, "Charlie") as (command, reply):
                expect_message(telegram, token, chat, "Charlie вошёл на сервер Minecraft\n@alex_updated @steve_telegram")
                command("telegram unlink alex_updated")
                reply("command.context.here")
                command("telegram link charlie_telegram")
                reply("Привязка сохранена")
                command("telegram unlink")
                reply("Привязка удалена")

    with dedicated_server(directory, telegram.server_port, options) as console:
        with vanilla_player(console, "Robin") as (command, reply):
            expect_message(telegram, token, chat, "Robin вошёл на сервер Minecraft\n@alex_updated @steve_telegram")
            console.send("op Robin")
            console.wait_for("Made Robin a server operator")
            command("telegram unlink ALEX_UPDATED")
            reply("Привязка удалена")
            command("telegram link alex_updated")
            reply("Привязка сохранена")

        console.wait_for("Robin left the game")
        with vanilla_player(console, "Robin"):
            expect_message(telegram, token, chat, "Robin вошёл на сервер Minecraft\n@steve_telegram")

        with vanilla_player(console, "Sam"):
            expect_message(telegram, token, chat, "Sam вошёл на сервер Minecraft\n@steve_telegram @alex_updated")


if __name__ == "__main__":
    main(run_checks)
