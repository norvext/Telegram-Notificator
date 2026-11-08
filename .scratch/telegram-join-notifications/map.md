# Уведомления о входе в Minecraft через Telegram

[Спецификация](spec.md)

## Зависимости

- [01. Первое уведомление](issues/01-send-join-notification.md)
- [02. Тема уведомлений](issues/02-send-to-topic.md) зависит от 01.
- [03. Привязка игрока](issues/03-link-telegram-username.md) зависит от 01.
- [04. Политика упоминаний](issues/04-configure-mention-policy.md) зависит от 03.
- [05. Подавление быстрых перезаходов](issues/05-suppress-quick-rejoins.md) зависит от 01.

## Decisions

- [01. Первое уведомление](issues/01-send-join-notification.md#answer)
- [02. Тема уведомлений](issues/02-send-to-topic.md#answer)
- [03. Привязка игрока](issues/03-link-telegram-username.md#answer)
- [04. Политика упоминаний](issues/04-configure-mention-policy.md#answer)
- [05. Подавление быстрых перезаходов](issues/05-suppress-quick-rejoins.md#answer)

Итоговая интеграция и исправления ревью в `codex/telegram-join-notifications` (`b5a44c0`).
