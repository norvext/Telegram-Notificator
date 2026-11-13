"""Check status, help, binding list, online counters and leave notifications on a dedicated server."""

import queue

from telegram_server import dedicated_server, expect_message, main, vanilla_player

TOKEN = "123:extras_test"
CHAT = -1001234567890


def expect_silence(telegram):
    try:
        unexpected = telegram.requests.get(timeout=1)
    except queue.Empty:
        return
    raise AssertionError(f"Unexpected Telegram request: {unexpected!r}")


def run_checks(directory, telegram, options):
    config = directory / "config" / "telegramnotificator-local.toml"
    config.parent.mkdir()
    config.write_text(
        "rejoinSeconds = 3\nnotifyOnStartup = false\n"
        'joinMessage = "<PlayerName> вошёл (<Online>/<MaxPlayers>)"\n'
        'leaveMessage = "<PlayerName> вышел (<Online>/<MaxPlayers>)"\n',
        encoding="utf-8", newline="\n")

    with dedicated_server(directory, telegram.server_port, options) as console:
        console.wait_for("Telegram Notificator не настроен. Выполните в консоли")
        console.send("telegram status")
        console.wait_for("Telegram Notificator не настроен: задайте токен и группу")
        console.wait_for("Токен: не задан")
        console.send(f"telegram token set {TOKEN}")
        console.wait_for("Токен сохранён")
        console.send(f"telegram chat set {CHAT} 7")
        console.wait_for("Группа уведомлений сохранена")
        console.send("telegram status")
        console.wait_for("Telegram Notificator настроен")
        console.wait_for("Токен: задан")
        console.wait_for(f"Группа: {CHAT}, тема: 7")
        console.wait_for("Сообщение о запуске: выключено, о выходе: выключено")
        console.send(f"telegram chat set {CHAT}")
        console.wait_for("Группа уведомлений сохранена")
        console.send("telegram")
        console.wait_for("telegram status — текущие настройки")
        console.send("telegram list")
        console.wait_for("Привязок нет")

        with vanilla_player(console, "Alex") as (command, reply):
            expect_message(telegram, TOKEN, CHAT, "Alex вошёл (1/20)")
            command("telegram status")
            reply("command.context.here")
            command("telegram")
            reply("Ваш Telegram не привязан")
            command("telegram link alex_tg")
            reply("Привязка сохранена")
            command("telegram")
            reply("Ваш Telegram: @alex_tg")
            console.send("telegram list")
            console.wait_for("Alex (в игре) — @alex_tg")
        console.wait_for("Alex left the game")
        # Leave notifications are off: nothing is sent even after the rejoin interval.
        console.send("telegram rejoin set 1")
        console.wait_for("Интервал перезахода сохранён")
        expect_silence(telegram)
        expect_silence(telegram)

        console.send("telegram leave true")
        console.wait_for("Уведомление о выходе включено")
        with vanilla_player(console, "Alex"):
            expect_message(telegram, TOKEN, CHAT, "Alex вошёл (1/20)")
            console.send("telegram rejoin set 3")
            console.wait_for("Интервал перезахода сохранён")
            with vanilla_player(console, "Steve"):
                expect_message(telegram, TOKEN, CHAT, "Steve вошёл (2/20)")
            console.wait_for("Steve left the game")
        console.wait_for("Alex left the game")
        # A quick rejoin hides both the pending leave and the join.
        with vanilla_player(console, "Alex"):
            expect_silence(telegram)
        console.wait_for("Alex left the game")
        expect_message(telegram, TOKEN, CHAT, "Steve вышел (0/20)")
        expect_message(telegram, TOKEN, CHAT, "Alex вышел (0/20)")
        console.send("telegram list")
        console.wait_for("Alex — @alex_tg")
        expect_silence(telegram)

        saved = config.read_text(encoding="utf-8")
        assert "# Токен бота от @BotFather" in saved, saved
        assert "notifyOnLeave = true" in saved
        assert "\r" not in saved
        assert TOKEN not in "".join(console.transcript)


if __name__ == "__main__":
    main(run_checks)
