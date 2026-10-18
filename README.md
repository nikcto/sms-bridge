# SMS ↔ Telegram Bridge

Полный проект состоит из `server/` (FastAPI + aiogram) и `android/` (Kotlin Android gateway).

## Быстрый запуск сервера Bothost

1. Создайте бота через [@BotFather](https://t.me/BotFather), получите token и узнайте Telegram ID девушки (например, через `@userinfobot`).
2. Скопируйте `server/.env.example` в `.env`, задайте `BOT_TOKEN`, `ADMIN_TELEGRAM_ID`, `ALLOWED_SMS_NUMBERS` (номер шлюзовой SIM и/или основной номер в E.164), случайные `DEVICE_TOKEN` и `WEBHOOK_SECRET`.
3. `python -m venv .venv && .venv/Scripts/pip install -r server/requirements.txt` (Linux: `.venv/bin/pip`).
4. Запустите на Bothost портом **3000**: `uvicorn server.main:app --host 0.0.0.0 --port 3000`.
5. Установите webhook: `https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<bothost-domain>/telegram/webhook/<WEBHOOK_SECRET>`.

Bothost автоматически выдаёт HTTPS-домен; в Android укажите этот `https://...` адрес без завершающего `/`.

## Android

### Сборка APK из PowerShell без Android Studio

Из корня проекта выполните:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\build-apk.ps1
```

Скрипт автоматически подготовит JDK 17, Gradle, Android SDK command-line tools, Android platform/build-tools и соберёт:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Если `winget` установит JDK в текущем окне не сразу, закройте PowerShell, откройте новое и повторите команду.

Откройте `android/` в Android Studio (JDK 17), Sync, Build APK. На телефоне установите APK, выдайте `SEND_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS` и автозапуск/исключение из battery optimization. В приложении укажите URL, device token, основной номер, разрешённые номера. На Android 4+ приложение может получать SMS через receiver без default-SMS роли; если прошивка требует её, назначьте приложение SMS по умолчанию в системных настройках. 

Нажмите «Подключить». WorkManager опрашивает сервер (минимальный период Android — около 15 минут), а кнопка «Check connection» запускает немедленный разовый опрос. Входящие SMS отфильтровываются по номерам. Для диагностики используйте Logcat с тегами `SmsBridge.Worker` и `SmsBridge.SmsSent`: там видны polling, HTTP-коды, запуск SMS и результат `SMS_SENT`. Тексты и токены в журнал не пишутся.

## Тестирование

Нажмите `/status` в Telegram (только admin), отправьте тестовый текст боту, проверьте доставку SMS, ответьте на SMS с разрешённого номера. Повторный poll/ack безопасен: сервер использует idempotency keys, Android — уникальные ID и локальную очередь. При рестарте сервера RAM-очередь теряется намеренно.

Сервис Telegram не принимает сообщения от других ID. Телефон должен быть включён, зарегистрирован в сети и иметь интернет, SIM-карту и возможность отправлять SMS. Для production используйте long random secrets, HTTPS, firewall/rate limiting Bothost и не публикуйте `.env`.
