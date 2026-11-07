"""Run the dedicated-server Telegram integration checks with python3 tests/telegram_server.py."""

import argparse
import contextlib
import json
import os
from pathlib import Path
import queue
import signal
import socket
import struct
import subprocess
import tempfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parent.parent


class TelegramStub(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        try:
            status, response, delay = self.server.responses.get_nowait()
        except queue.Empty:
            status, response, delay = 200, b'{"ok":true,"result":{}}', 0
        self.server.requests.put((self.path, body))
        time.sleep(delay)
        self.send_response(status)
        self.end_headers()
        try:
            self.wfile.write(response)
        except BrokenPipeError:
            pass

    def log_message(self, *_args):
        pass


class Console:
    def __init__(self, process, minecraft_port):
        self.process = process
        self.minecraft_port = minecraft_port
        self.lines = queue.Queue()
        self.transcript = []
        threading.Thread(target=self.read_output, daemon=True).start()

    def read_output(self):
        for line in self.process.stdout:
            self.transcript.append(line)
            self.lines.put(line)
        self.lines.put(None)

    def send(self, command):
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()

    def wait_for(self, message, timeout=15):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = self.lines.get(timeout=deadline - time.monotonic())
            except queue.Empty:
                break
            if line is None:
                break
            if message in line:
                return
        raise AssertionError(f"Missing server output {message!r}\n" + "".join(self.transcript[-50:]))


@contextlib.contextmanager
def dedicated_server(directory, api_port, gradle_options=()):
    init = directory / "test.init.gradle"
    init.write_text("""
gradle.projectsEvaluated {
    rootProject.tasks.named('runServer', JavaExec).configure {
        workingDir project.file(System.getenv('TELEGRAM_TEST_DIRECTORY'))
        standardInput = System.in
        systemProperty 'telegramnotificator.apiBase', System.getenv('TELEGRAM_TEST_API')
        systemProperty 'forge.logging.console.level', 'info'
    }
}
""")
    with socket.socket() as port:
        port.bind(("127.0.0.1", 0))
        minecraft_port = port.getsockname()[1]
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={minecraft_port}\n"
        "online-mode=false\nwhite-list=false\nenforce-secure-profile=false\n"
        "network-compression-threshold=-1\npause-when-empty-seconds=0\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nop-permission-level=1\n"
    )
    process = subprocess.Popen(
        ["./gradlew", *gradle_options, "--console=plain", "--no-daemon", "--no-configuration-cache", "-I", str(init), "runServer"],
        cwd=ROOT,
        env={**os.environ, "TELEGRAM_TEST_DIRECTORY": str(directory),
             "TELEGRAM_TEST_API": f"http://127.0.0.1:{api_port}"},
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", start_new_session=True,
    )
    console = Console(process, minecraft_port)
    try:
        console.wait_for('Done (', timeout=240)
        yield console
    finally:
        if process.poll() is None:
            try:
                console.send("stop")
                process.wait(timeout=15)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    process.wait(timeout=15)


def varint(value):
    encoded = bytearray()
    while value >= 128:
        encoded.append((value & 127) | 128)
        value >>= 7
    encoded.append(value)
    return bytes(encoded)


def read_varint(stream):
    value = 0
    for offset in range(0, 35, 7):
        byte = stream.read(1)
        if not byte:
            raise EOFError("Minecraft closed the connection")
        value |= (byte[0] & 127) << offset
        if byte[0] < 128:
            return value
    raise ValueError("Invalid Minecraft VarInt")


def mc_string(value):
    data = value.encode("utf-8")
    return varint(len(data)) + data


@contextlib.contextmanager
def vanilla_player(console, name):
    """Connect with the 26.3 vanilla protocol through login and configuration."""
    address = "127.0.0.1"
    port = console.minecraft_port
    with socket.create_connection((address, port), timeout=15) as connection, connection.makefile("rb") as stream:
        def send(packet_id, payload=b""):
            packet = varint(packet_id) + payload
            connection.sendall(varint(len(packet)) + packet)

        def receive():
            length = read_varint(stream)
            return stream.read(length)

        send(0, varint(777) + mc_string(address) + struct.pack(">H", port) + varint(2))  # Login intention
        send(0, mc_string(name) + uuid.uuid4().bytes)  # Login hello
        login = receive()
        assert login[0] == 2, f"Expected login success, received {login!r}"
        send(3)  # Login acknowledged

        while True:
            packet = receive()
            packet_id, payload = packet[0], packet[1:]
            if packet_id == 5:  # Configuration ping
                send(5, payload)
            elif packet_id == 15:  # Known packs
                send(7, b"\0")
            elif packet_id == 3:  # Finish configuration
                send(3)
                break
            elif packet_id == 2:
                raise AssertionError("Vanilla player disconnected during configuration")
        console.wait_for(name + " joined the game")
        def command(text):
            send(7, mc_string(text))  # Unsigned chat command

        def wait_for(message):
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                connection.settimeout(deadline - time.monotonic())
                if message.encode("utf-8") in receive():
                    return
            raise AssertionError(f"Missing player feedback {message!r}")

        yield command, wait_for


def expect_message(telegram, token, chat, text):
    assert telegram.requests.get(timeout=5) == (
        f"/bot{token}/sendMessage",
        {"chat_id": chat, "text": text},
    )


def run_checks(directory, telegram, options):
    initial_token = "123:integration_test"
    replacement_token = "456:replacement_test"
    initial_chat = -1001234567890
    replacement_chat = -1001234567891
    test_message = "Пробное сообщение от Minecraft"

    with dedicated_server(directory, telegram.server_port, options) as console:
        console.send("telegram test")
        console.wait_for("Задайте токен и группу уведомлений")
        console.send(f"telegram token set {initial_token}")
        console.send(f"telegram chat set {initial_chat}")
        console.send("telegram test")
        console.wait_for("Пробное сообщение отправлено")
        expect_message(telegram, initial_token, initial_chat, test_message)

        with vanilla_player(console, "Alex") as (command, reply):
            expect_message(telegram, initial_token, initial_chat, "Alex вошёл на сервер Minecraft")
            command("telegram test")
            reply("command.context.here")
            assert telegram.requests.empty()

            console.send("op Alex")
            console.wait_for("Made Alex a server operator")
            command(f"telegram token set {replacement_token}")
            reply("Токен сохранён")
            command(f"telegram chat set {replacement_chat}")
            reply("Группа уведомлений сохранена")
            command("telegram test")
            reply("Пробное сообщение отправлено")
            expect_message(telegram, replacement_token, replacement_chat, test_message)

        assert initial_token not in "".join(console.transcript)
        assert replacement_token not in "".join(console.transcript)

    with dedicated_server(directory, telegram.server_port, options) as console:
        telegram.responses.put((401, replacement_token.encode(), 0))
        console.send("telegram test")
        console.wait_for("Пробное сообщение не отправлено: HTTP 401")
        expect_message(telegram, replacement_token, replacement_chat, test_message)

        telegram.responses.put((200, b'{"ok":true}', 6))
        with vanilla_player(console, "Alex"):
            expect_message(telegram, replacement_token, replacement_chat, "Alex вошёл на сервер Minecraft")
            console.send("list")
            console.wait_for("players online: Alex", timeout=2)
            console.wait_for("истекло время ожидания", timeout=7)
            assert telegram.requests.empty()

            console.send("telegram token remove extra")
            console.wait_for("Incorrect argument for command")
            console.send("telegram test")
            console.wait_for("Пробное сообщение отправлено")
            expect_message(telegram, replacement_token, replacement_chat, test_message)

            console.send("telegram token remove")
            console.wait_for("Токен удалён")
            console.send("telegram token remove")
            console.wait_for("Токен не задан")
            console.send("telegram test")
            console.wait_for("Задайте токен и группу уведомлений")
            assert telegram.requests.empty()
            assert replacement_token not in "".join(console.transcript)


def main(checks=run_checks):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use only cached Gradle dependencies")
    options = ("--offline",) if parser.parse_args().offline else ()
    with tempfile.TemporaryDirectory() as directory, ThreadingHTTPServer(("127.0.0.1", 0), TelegramStub) as telegram:
        telegram.requests = queue.Queue()
        telegram.responses = queue.Queue()
        threading.Thread(target=telegram.serve_forever, daemon=True).start()
        try:
            checks(Path(directory), telegram, options)
        finally:
            telegram.shutdown()


if __name__ == "__main__":
    main()
