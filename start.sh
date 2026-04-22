#!/bin/bash
SESSION_NAME="telegram_bot"

# Завершаем старую сессию, если она существует, чтобы гарантировать "новый экран"
screen -S $SESSION_NAME -X quit 2>/dev/null

# Запускаем новую сессию в фоновом режиме (detached)
screen -dmS $SESSION_NAME bash -c "
while true; do
    echo \"Starting Telegram Bot...\"
    java -jar -Dfile.encoding=UTF-8 LightsMonitorBot-1.0-SNAPSHOT.jar
    echo \"Bot stopped. Restarting in 5 seconds...\"
    sleep 5
done
"

echo "Бот запущен в сессии screen: $SESSION_NAME"
echo "Используйте 'screen -ls' для просмотра сессий и 'screen -r $SESSION_NAME' для подключения к логам."
