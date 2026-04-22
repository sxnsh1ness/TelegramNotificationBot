package com.bot;

import com.bot.model.Region;
import com.bot.model.ScheduleResponse;
import com.bot.model.UserSetting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerService {
    private final NotificationBot bot;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private Map<String, Map<String, Object>> schedulesCache = new HashMap<>();
    private Set<String> alertHistory = new HashSet<>();
    private Map<String, String> morningSent = new HashMap<>();

    public SchedulerService(NotificationBot bot) {
        this.bot = bot;
    }

    public void start() {
        // Update data every X seconds
        scheduler.scheduleAtFixedRate(this::updateData, 0, Config.UPDATE_INTERVAL, TimeUnit.SECONDS);
        
        // Check for alerts every minute
        scheduler.scheduleAtFixedRate(this::checkAlerts, 1, 1, TimeUnit.MINUTES);
    }

    private void updateData() {
        try {
            System.out.println("Updating API data...");
            ScheduleResponse data = ApiService.fetchApiData();
            if (data != null && data.getRegions() != null) {
                String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                
                List<String[]> subs = DatabaseService.getAllSubscriptions();
                for (String[] sub : subs) {
                    String regionName = sub[0];
                    String queue = sub[1];
                    
                    Region region = data.getRegions().stream()
                            .filter(r -> r.getNameUa().equals(regionName))
                            .findFirst().orElse(null);
                    
                    if (region != null && region.getSchedule() != null) {
                        Map<String, Object> queueData = region.getSchedule().get(queue);
                        if (queueData != null) {
                            Object todaySch = queueData.get(today);
                            if (todaySch != null) {
                                double offHours = ApiService.calculateOffHours(todaySch);
                                DatabaseService.saveStats(regionName, queue, today, offHours);
                            }
                            schedulesCache.put(regionName + ":" + queue, queueData);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkAlerts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String currTime = now.format(DateTimeFormatter.ofPattern("HH:mm"));
            String todayStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            if (currTime.equals("00:00")) {
                alertHistory.clear();
                morningSent.clear();
            }

            // Morning summary at 06:00
            if (currTime.equals("06:00")) {
                sendMorningSummaries(todayStr);
            }

            int[] possibleIntervals = {5, 15, 30, 60};

            // Pre-alerts
            for (Map.Entry<String, Map<String, Object>> entry : schedulesCache.entrySet()) {
                String[] parts = entry.getKey().split(":");
                String region = parts[0];
                String queue = parts[1];
                Map<String, Object> queueData = entry.getValue();

                Object todaySch = queueData.get(todayStr);
                if (todaySch == null) continue;

                // Check for Outage starts
                List<String> outageStarts = getOutageStarts(todaySch);
                for (String start : outageStarts) {
                    for (int mins : possibleIntervals) {
                        if (isTimeMatches(now, start, mins)) {
                            String msg = "⏳ **Скоро відключення (через " + (mins == 60 ? "1 год" : mins + " хв") + ").**";
                            sendAlert(region, queue, msg, mins, "outage");
                        }
                    }
                }

                // Check for Power returns
                List<String> powerReturns = getPowerReturns(todaySch);
                for (String ret : powerReturns) {
                    for (int mins : possibleIntervals) {
                        if (isTimeMatches(now, ret, mins)) {
                            String msg = "💡 **Скоро буде світло (через " + (mins == 60 ? "1 год" : mins + " хв") + ").**";
                            sendAlert(region, queue, msg, mins, "return");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getOutageStarts(Object schedule) {
        List<String> starts = new ArrayList<>();
        if (schedule instanceof List) {
            for (Object item : (List) schedule) {
                if (item instanceof String) starts.add(((String) item).split("-")[0]);
            }
        } else if (schedule instanceof Map) {
            Map<String, Object> intervals = (Map<String, Object>) schedule;
            for (Map.Entry<String, Object> slot : intervals.entrySet()) {
                if (isOutageSlot(slot.getValue())) {
                    String time = slot.getKey();
                    String prevTime = getPreviousTimeSlot(time);
                    if (!isOutageSlot(intervals.get(prevTime))) starts.add(time);
                }
            }
        }
        return starts;
    }

    private List<String> getPowerReturns(Object schedule) {
        List<String> returns = new ArrayList<>();
        if (schedule instanceof List) {
            for (Object item : (List) schedule) {
                if (item instanceof String) {
                    String end = ((String) item).split("-")[1];
                    if (!"00:00".equals(end) && !"24:00".equals(end)) returns.add(end);
                }
            }
        } else if (schedule instanceof Map) {
            Map<String, Object> intervals = (Map<String, Object>) schedule;
            for (Map.Entry<String, Object> slot : intervals.entrySet()) {
                if (!isOutageSlot(slot.getValue())) {
                    String time = slot.getKey();
                    String prevTime = getPreviousTimeSlot(time);
                    if (isOutageSlot(intervals.get(prevTime))) returns.add(time);
                }
            }
        }
        return returns;
    }

    private boolean isOutageSlot(Object val) {
        return val instanceof Integer && (Integer) val == 2;
    }

    private boolean isTimeMatches(LocalDateTime now, String targetTime, int minutesBefore) {
        try {
            LocalDateTime target = now.plusMinutes(minutesBefore);
            return target.format(DateTimeFormatter.ofPattern("HH:mm")).equals(targetTime);
        } catch (Exception e) { return false; }
    }

    private String getPreviousTimeSlot(String time) {
        try {
            if (time == null) return null;
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            int total = hour * 60 + min - 30;
            if (total < 0) return null;
            return String.format("%02d:%02d", total / 60, total % 60);
        } catch (Exception e) { return null; }
    }

    private void sendMorningSummaries(String date) {
        try {
            List<String[]> subs = DatabaseService.getAllSubscriptions();
            for (String[] sub : subs) {
                String region = sub[0];
                String queue = sub[1];
                String key = region + ":" + queue;
                
                if (morningSent.containsKey(key)) continue;

                Map<String, Object> queueData = schedulesCache.get(key);
                if (queueData != null) {
                    Object todaySch = queueData.get(date);
                    if (todaySch != null) {
                        String message = "☀️ **Добрий ранок! Графік на сьогодні:**\n" + formatSchedule(todaySch);
                        broadcastToQueue(region, queue, message, "morning", 0);
                        morningSent.put(key, date);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatSchedule(Object schedule) {
        if (schedule instanceof List) {
            List<String> list = (List<String>) schedule;
            if (list.isEmpty()) return "✅ Відключень не передбачено.\n";
            StringBuilder sb = new StringBuilder();
            for (String s : list) sb.append("🔴 **").append(s).append("**\n");
            return sb.toString();
        } else if (schedule instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) schedule;
            List<String> intervals = new ArrayList<>();
            String start = null;
            List<String> sortedTimes = new ArrayList<>(map.keySet());
            Collections.sort(sortedTimes);
            for (String t : sortedTimes) {
                if (isOutageSlot(map.get(t))) {
                    if (start == null) start = t;
                } else if (start != null) {
                    intervals.add(start + "-" + t);
                    start = null;
                }
            }
            if (start != null) intervals.add(start + "-00:00");
            if (intervals.isEmpty()) return "✅ Відключень не передбачено.\n";
            StringBuilder sb = new StringBuilder();
            for (String i : intervals) sb.append("🔴 **").append(i).append("**\n");
            return sb.toString();
        }
        return "Немає відключень.";
    }

    private void sendAlert(String region, String queue, String message, int minutes, String type) {
        String alertId = region + ":" + queue + ":" + type + ":" + minutes;
        if (alertHistory.contains(alertId)) return;
        
        broadcastToQueue(region, queue, message, type, minutes);
        alertHistory.add(alertId);
    }

    private void broadcastToQueue(String region, String queue, String message, String type, int minutes) {
        try {
            List<Long> users = DatabaseService.getUsersByQueue(region, queue);
            for (Long userId : users) {
                UserSetting settings = DatabaseService.getUserSettings(userId);
                if (settings != null) {
                    if ("morning".equals(type) && !settings.isNotifyChanges()) continue;
                    
                    if ("outage".equals(type)) {
                        if (!settings.isNotifyOutage()) continue;
                        if (settings.getNotifyBefore() != minutes) continue;
                    } else if ("return".equals(type)) {
                        if (!settings.isNotifyReturn()) continue;
                        if (settings.getNotifyReturnBefore() != minutes) continue;
                    }
                    
                    bot.sendDirectMessage(userId, message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
