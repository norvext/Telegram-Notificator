"""Run dedicated-server rejoin checks with python3 tests/telegram_rejoins.py."""

from contextlib import ExitStack
import queue
import time

from telegram_server import dedicated_server, expect_message, main, vanilla_player


def expect_join(telegram, name):
    path, payload = telegram.requests.get(timeout=5)
    assert path == "/bot123:integration_test/sendMessage"
    assert payload["chat_id"] == -1001234567890
    assert name in payload["text"]


def expect_no_join(telegram):
    try:
        unexpected = telegram.requests.get(timeout=1)
    except queue.Empty:
        return
    raise AssertionError(f"Unexpected Telegram request on rejoin: {unexpected!r}")


def run_checks(directory, telegram, options):
    with dedicated_server(directory, telegram.server_port, options) as console:
        console.send("telegram token set 123:integration_test")
        console.wait_for("Токен сохранён")
        console.send("telegram chat set -1001234567890")
        console.wait_for("Группа уведомлений сохранена")

        with vanilla_player(console, "Alex"):
            expect_join(telegram, "Alex")
        console.wait_for("Alex left the game")
        with vanilla_player(console, "Alex"):
            expect_no_join(telegram)
        console.wait_for("Alex left the game")

        with ExitStack() as online:
            for name in ("Steve", "Casey"):
                online.enter_context(vanilla_player(console, name))
                expect_join(telegram, name)
            console.send("stop")
            assert console.process.wait(timeout=15) == 0

    with dedicated_server(directory, telegram.server_port, options) as console:
        expect_message(telegram, "123:integration_test", -1001234567890, "Сервер Minecraft запущен")
        for name in ("Alex", "Steve", "Casey"):
            with vanilla_player(console, name):
                expect_no_join(telegram)
            console.wait_for(name + " left the game")


        console.send("telegram rejoin set 8")
        console.wait_for("Интервал перезахода сохранён")
        console.send("telegram rejoin set -1")
        console.wait_for("Integer must not be less than 0")
        with vanilla_player(console, "Alex") as (command, reply):
            expect_no_join(telegram)
            command("telegram rejoin set 0")
            reply("command.context.here")
            time.sleep(4)
        console.wait_for("Alex left the game")
        time.sleep(4)
        with vanilla_player(console, "Alex") as (command, reply):
            expect_no_join(telegram)
            console.send("op Alex")
            console.wait_for("Made Alex a server operator")
            command("telegram rejoin set 0")
            reply("Интервал перезахода сохранён")
        console.wait_for("Alex left the game")
        with vanilla_player(console, "Alex"):
            expect_join(telegram, "Alex")

    with dedicated_server(directory, telegram.server_port, options) as console:
        expect_message(telegram, "123:integration_test", -1001234567890, "Сервер Minecraft запущен")
        with vanilla_player(console, "Alex"):
            expect_join(telegram, "Alex")


if __name__ == "__main__":
    main(run_checks)
