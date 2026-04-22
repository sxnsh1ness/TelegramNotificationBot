package com.bot;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Config {
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    public static final String BOT_TOKEN = dotenv.get("BOT_TOKEN", "your_bot_token");
    public static final String BOT_USERNAME = dotenv.get("BOT_USERNAME", "your_bot_username");
    public static final List<Long> ADMIN_IDS = parseAdminIds(dotenv.get("ADMIN_IDS", ""));
    public static final String CHANNEL_ID = dotenv.get("CHANNEL_ID", "");
    public static final String PRIMARY_API_URL = dotenv.get("PRIMARY_API_URL", "");
    public static final String BACKUP_API_URL = dotenv.get("BACKUP_API_URL", "");
    public static final int UPDATE_INTERVAL = Integer.parseInt(dotenv.get("UPDATE_INTERVAL", "300"));
    public static final int FAILOVER_TIMEOUT = Integer.parseInt(dotenv.get("FAILOVER_TIMEOUT", "7200"));
    public static final int RECOVERY_CHECK_INTERVAL = Integer.parseInt(dotenv.get("RECOVERY_CHECK_INTERVAL", "3600"));
    public static final String DB_NAME = dotenv.get("DB_NAME", "svitlo_bot.db");
    public static final String HOE_SITE_URL = dotenv.get("HOE_SITE_URL", "https://hoe.com.ua/page/pogodinni-vidkljuchennja");

    private static List<Long> parseAdminIds(String adminIdsStr) {
        if (adminIdsStr == null || adminIdsStr.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(adminIdsStr.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
