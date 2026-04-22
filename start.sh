#!/bin/bash

# Configuration
APP_NAME="TelegramNotificationBot-1.0-SNAPSHOT.jar"
PID_FILE="bot.pid"

# Kill existing process if running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null; then
        echo "Stopping existing bot (PID: $PID)..."
        kill $PID
        sleep 2
    fi
    rm "$PID_FILE"
fi

# Also kill any leftover java process with the same jar name just in case
pkill -f "$APP_NAME"

echo "Starting bot..."
# Run the bot in background and save PID
nohup java -jar "$APP_NAME" > bot.log 2>&1 &
echo $! > "$PID_FILE"

echo "Bot started with PID $(cat $PID_FILE)"
tail -f bot.log
