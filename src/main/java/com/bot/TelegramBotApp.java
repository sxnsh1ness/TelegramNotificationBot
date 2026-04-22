package com.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramBotApp {
    public static void main(String[] args) {
        try {
            // Инициализация API
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Создание и регистрация бота
            NotificationBot bot = new NotificationBot();
            botsApi.registerBot(bot);

            // Инициализация планировщика
            SchedulerService scheduler = new SchedulerService(bot);
            scheduler.start();

            System.out.println("Бот успешно запущен и планировщик активен!");

            // Пример отправки уведомления при запуске
            // bot.sendNotification("Бот запущен и готов к работе!");

        } catch (TelegramApiException e) {
            e.printStackTrace();
            System.err.println("Ошибка при запуске бота: " + e.getMessage());
        }
    }
}
