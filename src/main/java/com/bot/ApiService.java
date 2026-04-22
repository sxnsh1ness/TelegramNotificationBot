package com.bot;

import com.bot.model.Region;
import com.bot.model.ScheduleResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiService {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private static ScheduleResponse cachedData = null;
    private static LocalDateTime lastFetchTime = null;
    private static final int CACHE_TTL_SECONDS = 60;

    private static String activeSource = "primary";
    private static LocalDateTime primaryDownSince = null;
    private static LocalDateTime lastPrimaryCheck = null;

    public static ScheduleResponse fetchApiData() {
        LocalDateTime now = LocalDateTime.now();

        if (lastFetchTime != null && Duration.between(lastFetchTime, now).getSeconds() < CACHE_TTL_SECONDS) {
            return cachedData;
        }

        ScheduleResponse primaryData = null;
        ScheduleResponse backupData = null;

        if ("primary".equals(activeSource)) {
            primaryData = fetchPrimaryApi();
            if (primaryData != null) {
                primaryDownSince = null;
                backupData = fetchBackupApi();
                if (backupData != null) {
                    primaryData = mergeApiData(primaryData, backupData);
                }
            } else {
                if (primaryDownSince == null) primaryDownSince = now;
                if (Duration.between(primaryDownSince, now).getSeconds() >= Config.FAILOVER_TIMEOUT) {
                    activeSource = "backup";
                    lastPrimaryCheck = now;
                }
                backupData = fetchBackupApi();
            }
        } else {
            // Recovery check
            if (lastPrimaryCheck == null || Duration.between(lastPrimaryCheck, now).getSeconds() >= Config.RECOVERY_CHECK_INTERVAL) {
                primaryData = fetchPrimaryApi();
                lastPrimaryCheck = now;
                if (primaryData != null) {
                    activeSource = "primary";
                    primaryDownSince = null;
                }
            }
            backupData = fetchBackupApi();
            if (primaryData != null && backupData != null) {
                backupData = mergeApiData(primaryData, backupData);
            }
        }

        ScheduleResponse data = (primaryData != null) ? primaryData : backupData;
        
        // Site integration (HOE)
        ScheduleResponse siteData = fetchHoeSite();
        if (siteData != null) {
            if (data == null) data = siteData;
            else mergeSiteData(data, siteData);
        }

        if (data != null) {
            cachedData = data;
            lastFetchTime = now;
        }

        return cachedData;
    }

    private static ScheduleResponse fetchPrimaryApi() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Config.PRIMARY_API_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseApiResponse(response.body());
            }
        } catch (Exception e) {
            System.err.println("Primary API Error: " + e.getMessage());
        }
        return null;
    }

    private static ScheduleResponse fetchBackupApi() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Config.BACKUP_API_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseApiResponse(response.body());
            }
        } catch (Exception e) {
            System.err.println("Backup API Error: " + e.getMessage());
        }
        return null;
    }

    private static ScheduleResponse parseApiResponse(String responseBody) throws Exception {
        // Try parsing directly first
        try {
            ScheduleResponse data = objectMapper.readValue(responseBody, ScheduleResponse.class);
            if (data != null && data.getRegions() != null && !data.getRegions().isEmpty()) {
                return normalizeRegionNames(data);
            }
        } catch (Exception ignored) {}

        // If direct parsing failed or returned no regions, check for the "body" wrapper
        Map<String, Object> raw = objectMapper.readValue(responseBody, Map.class);
        if (raw.containsKey("body") && raw.get("body") instanceof String) {
            String bodyStr = (String) raw.get("body");
            ScheduleResponse data = objectMapper.readValue(bodyStr, ScheduleResponse.class);
            return normalizeRegionNames(data);
        }

        return null;
    }

    private static ScheduleResponse normalizeRegionNames(ScheduleResponse data) {
        if (data == null || data.getRegions() == null) return data;
        for (Region region : data.getRegions()) {
            String name = region.getNameUa();
            if (name != null && name.endsWith(" область")) {
                region.setNameUa(name.replace(" область", "").trim());
            }
            if ("Хмельницька область".equals(name)) {
                region.setNameUa("Хмельницька");
            }
        }
        return data;
    }

    private static ScheduleResponse mergeApiData(ScheduleResponse primary, ScheduleResponse backup) {
        if (primary == null) return backup;
        if (backup == null) return primary;

        Map<String, Region> backupByName = new HashMap<>();
        for (Region r : backup.getRegions()) backupByName.put(r.getNameUa(), r);

        Set<String> primaryNames = new HashSet<>();
        for (Region r : primary.getRegions()) {
            primaryNames.add(r.getNameUa());
            Region bRegion = backupByName.get(r.getNameUa());
            if (bRegion != null && bRegion.getSchedule() != null && r.getSchedule() != null) {
                for (Map.Entry<String, Map<String, Object>> entry : bRegion.getSchedule().entrySet()) {
                    String queueId = entry.getKey();
                    Map<String, Object> bQueueData = entry.getValue();
                    Map<String, Object> pQueueData = r.getSchedule().get(queueId);
                    if (pQueueData == null) {
                        r.getSchedule().put(queueId, bQueueData);
                    } else {
                        for (String date : bQueueData.keySet()) {
                            if (!pQueueData.containsKey(date)) {
                                pQueueData.put(date, bQueueData.get(date));
                            }
                        }
                    }
                }
            }
        }

        for (Region bRegion : backup.getRegions()) {
            if (!primaryNames.contains(bRegion.getNameUa())) {
                primary.getRegions().add(bRegion);
            }
        }

        if (primary.getDateToday() == null) primary.setDateToday(backup.getDateToday());
        if (primary.getDateTomorrow() == null) primary.setDateTomorrow(backup.getDateTomorrow());

        return primary;
    }

    private static void mergeSiteData(ScheduleResponse data, ScheduleResponse siteData) {
        if (siteData.getRegions() == null || siteData.getRegions().isEmpty()) return;
        Region siteRegion = siteData.getRegions().get(0);
        boolean found = false;
        for (Region r : data.getRegions()) {
            if ("Хмельницька".equals(r.getNameUa())) {
                r.setSchedule(siteRegion.getSchedule());
                found = true;
                break;
            }
        }
        if (!found) data.getRegions().add(siteRegion);
    }

    private static ScheduleResponse fetchHoeSite() {
        try {
            Document doc = Jsoup.connect(Config.HOE_SITE_URL).get();
            Element postDiv = doc.selectFirst("div.post");
            if (postDiv == null) return null;

            Map<String, Map<String, Object>> scheduleMap = new HashMap<>();
            String currentDateStr = null;

            for (Element element : postDiv.children()) {
                String text = element.text().toLowerCase();
                Pattern datePattern = Pattern.compile("(\\d{1,2})\\s+([а-яієї]+)");
                Matcher matcher = datePattern.matcher(text);
                if (matcher.find()) {
                    String day = matcher.group(1);
                    String monthName = matcher.group(2);
                    // Simplified month parsing
                    String month = getMonthNumber(monthName);
                    if (month != null) {
                        int year = LocalDateTime.now().getYear();
                        currentDateStr = String.format("%d-%s-%02d", year, month, Integer.parseInt(day));
                    }
                }

                if ("ul".equals(element.tagName()) && currentDateStr != null) {
                    for (Element li : element.select("li")) {
                        parseQueueLine(li.text(), currentDateStr, scheduleMap);
                    }
                }
            }

            if (scheduleMap.isEmpty()) return null;

            Region region = new Region();
            region.setNameUa("Хмельницька");
            region.setSchedule(scheduleMap);
            ScheduleResponse response = new ScheduleResponse();
            response.setRegions(List.of(region));
            return response;

        } catch (Exception e) {
            System.err.println("HOE Site Parser Error: " + e.getMessage());
        }
        return null;
    }

    private static String getMonthNumber(String name) {
        Map<String, String> UA_MONTHS = Map.ofEntries(
            Map.entry("січня", "01"), Map.entry("лютого", "02"), Map.entry("березня", "03"),
            Map.entry("квітня", "04"), Map.entry("травня", "05"), Map.entry("червня", "06"),
            Map.entry("липня", "07"), Map.entry("серпня", "08"), Map.entry("вересня", "09"),
            Map.entry("жовтня", "10"), Map.entry("листопада", "11"), Map.entry("грудня", "12")
        );
        return UA_MONTHS.get(name);
    }

    private static void parseQueueLine(String text, String dateStr, Map<String, Map<String, Object>> scheduleMap) {
        Pattern queuePattern = Pattern.compile("(\\d\\.\\d)");
        Matcher matcher = queuePattern.matcher(text);
        if (!matcher.find()) return;
        String queueId = matcher.group(1);

        Pattern timePattern = Pattern.compile("(\\d{2}:\\d{2})\\s*(?:до|-|–|—)\\s*(\\d{2}:\\d{2})");
        Matcher timeMatcher = timePattern.matcher(text);
        List<String> intervals = new ArrayList<>();
        while (timeMatcher.find()) {
            intervals.add(timeMatcher.group(1) + "-" + timeMatcher.group(2));
        }

        scheduleMap.computeIfAbsent(queueId, k -> new HashMap<>()).put(dateStr, intervals);
    }

    public static double calculateOffHours(Object scheduleData) {
        if (scheduleData instanceof List) {
            double totalMinutes = 0;
            for (Object item : (List) scheduleData) {
                try {
                    String[] parts = ((String) item).split("-");
                    String start = parts[0];
                    String end = parts[1].equals("24:00") ? "23:59" : parts[1];
                    int bonus = parts[1].equals("24:00") ? 1 : 0;
                    
                    String[] sParts = start.split(":");
                    String[] eParts = end.split(":");
                    int sMin = Integer.parseInt(sParts[0]) * 60 + Integer.parseInt(sParts[1]);
                    int eMin = Integer.parseInt(eParts[0]) * 60 + Integer.parseInt(eParts[1]);
                    
                    int diff = eMin - sMin + bonus;
                    if (diff < 0) diff += 24 * 60;
                    totalMinutes += diff;
                } catch (Exception ignored) {}
            }
            return Math.round(totalMinutes / 6.0) / 10.0;
        } else if (scheduleData instanceof Map) {
            long count = ((Map<?, ?>) scheduleData).values().stream()
                    .filter(v -> v instanceof Integer && (Integer) v == 2)
                    .count();
            return count * 0.5;
        }
        return 0;
    }
}
