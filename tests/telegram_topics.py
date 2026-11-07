"""Run the Telegram topic routing checks with python3 tests/telegram_topics.py."""

from telegram_server import dedicated_server, expect_message, main, vanilla_player


TOKEN = "123:topic_test"
CHAT = -1001234567890
TOPIC = 123
OPERATOR_TOPIC = 456
TEST_MESSAGE = "Пробное сообщение от Minecraft"


def expect_topic_message(telegram, topic, text):
    assert telegram.requests.get(timeout=5) == (
        f"/bot{TOKEN}/sendMessage",
        {"chat_id": CHAT, "message_thread_id": topic, "text": text},
    )


def run_checks(directory, telegram, options):
    with dedicated_server(directory, telegram.server_port, options) as console:
        console.send(f"telegram token set {TOKEN}")
        console.send(f"telegram chat set {CHAT} {TOPIC}")
        console.send("telegram test")
        console.wait_for("Пробное сообщение отправлено")
        expect_topic_message(telegram, TOPIC, TEST_MESSAGE)

        config = directory / "config" / "telegramnotificator-local.toml"
        backup = config.with_suffix(".backup")
        config.rename(backup)
        config.mkdir()
        (config / "block_replacement").touch()
        console.send(f"telegram chat set {CHAT - 1} {OPERATOR_TOPIC}")
        console.wait_for("Не удалось сохранить настройки Telegram")
        console.send("telegram test")
        console.wait_for("Пробное сообщение отправлено")
        expect_topic_message(telegram, TOPIC, TEST_MESSAGE)
        (config / "block_replacement").unlink()
        config.rmdir()
        backup.rename(config)

        telegram.responses.put((400, b'{"ok":false,"description":"Bad Request: message thread not found"}', 0))
        console.send("telegram test")
        console.wait_for("Не удалось отправить сообщение Telegram: HTTP 400: проверьте группу и тему уведомлений")
        console.wait_for("Пробное сообщение не отправлено: HTTP 400: проверьте группу и тему уведомлений")
        expect_topic_message(telegram, TOPIC, TEST_MESSAGE)

        with vanilla_player(console, "Alex") as (command, reply):
            expect_topic_message(telegram, TOPIC, "Alex вошёл на сервер Minecraft")
            command(f"telegram chat set {CHAT} {OPERATOR_TOPIC}")
            reply("command.context.here")
            console.send("telegram test")
            console.wait_for("Пробное сообщение отправлено")
            expect_topic_message(telegram, TOPIC, TEST_MESSAGE)

            console.send("op Alex")
            console.wait_for("Made Alex a server operator")
            command(f"telegram chat set {CHAT} {OPERATOR_TOPIC}")
            reply("Группа уведомлений сохранена")

    with dedicated_server(directory, telegram.server_port, options) as console:
        console.send("telegram test")
        console.wait_for("Пробное сообщение отправлено")
        expect_topic_message(telegram, OPERATOR_TOPIC, TEST_MESSAGE)
        with vanilla_player(console, "Steve"):
            expect_topic_message(telegram, OPERATOR_TOPIC, "Steve вошёл на сервер Minecraft")

        plain_chat = -1001234567891
        console.send(f"telegram chat set {plain_chat}")
        console.send("telegram test")
        console.wait_for("Пробное сообщение отправлено")
        expect_message(telegram, TOKEN, plain_chat, TEST_MESSAGE)
        with vanilla_player(console, "Casey"):
            expect_message(telegram, TOKEN, plain_chat, "Casey вошёл на сервер Minecraft")
        assert telegram.requests.empty()


if __name__ == "__main__":
    main(run_checks)
