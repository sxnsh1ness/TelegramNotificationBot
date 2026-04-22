#!/bin/bash
while true; do
    echo "Starting Discord Bot..."
    java -jar -Dfile.encoding=UTF-8 LightsMonitorBot-1.0-SNAPSHOT.jar
    echo "Bot stopped. Restarting in 5 seconds..."
    sleep 5
done
