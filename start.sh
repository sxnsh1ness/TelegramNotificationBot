#!/bin/bash

# Configuration
APP_NAME="LightsMonitorBot-1.0-SNAPSHOT.jar"
SCREEN_NAME="telegram_bot"

# Kill existing screen session if running
screen -S "$SCREEN_NAME" -X quit 2>/dev/null
pkill -f "$APP_NAME"

echo "Starting bot in screen session '$SCREEN_NAME'..."
# Run the bot inside a detached screen session
screen -dmS "$SCREEN_NAME" java -jar "$APP_NAME"

echo "Bot started! To view logs, run: screen -r $SCREEN_NAME"
echo "Or check bot.log if the app redirects output there."

