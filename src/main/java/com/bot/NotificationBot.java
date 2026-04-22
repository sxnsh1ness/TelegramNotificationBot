package com.bot;

import com.bot.model.UserSetting;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NotificationBot extends TelegramLongPollingBot {
    private final Map<Long, String> userStates = new HashMap<>();

    @Override
    public String getBotUsername() {
        return Config.BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return Config.BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            org.telegram.telegrambots.meta.api.objects.User user = update.getMessage().getFrom();
            String username = user.getUserName();
            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            String fullName = firstName + (lastName != null ? " " + lastName : "");

            handleCommand(update.getMessage().getChatId(), update.getMessage().getText(), username, fullName);
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery().getMessage().getChatId(),
                    update.getCallbackQuery().getMessage().getMessageId(),
                    update.getCallbackQuery().getData());
        }
    }

    private void handleCommand(long chatId, String text, String username, String fullName) {
        if (userStates.containsKey(chatId)) {
            String state = userStates.get(chatId);
            if (state.equals("AWAITING_BROADCAST")) {
                handleBroadcastMessage(chatId, text);
                return;
            } else if (state.equals("AWAITING_SUPPORT")) {
                handleSupportMessage(chatId, text, username, fullName);
                return;
            } else if (state.startsWith("AWAITING_REPLY:")) {
                handleAdminReply(chatId, text, state.split(":")[1]);
                return;
            }
        }

        if (text.startsWith("/start")) {

            sendStartMenu(chatId);
        } else if (text.startsWith("/settings") || text.equals("⚙️ Налаштування")) {
            sendSettingsMenu(chatId);
        } else if (text.startsWith("/stats") || text.equals("📊 Аналітика")) {
            sendStats(chatId);
        } else if (text.equals("💬 Підтримка")) {
            sendSupportMenu(chatId);
        } else if (text.equals("📅 Графік на сьогодні")) {
            sendSchedule(chatId, true);
        } else if (text.equals("🔮 Графік на завтра")) {
            sendSchedule(chatId, false);
        } else if (text.equals("🔐 Адмін-панель") && chatId == 5450073627L) {
            sendAdminPanel(chatId);
        }
    }

    private void sendAdminPanel(long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("🔐 **Адмін-панель**\n\nОберіть дію:")
                .parseMode("Markdown")
                .replyMarkup(createAdminPanelKeyboard())
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup createMainMenuKeyboard(long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📅 Графік на сьогодні"));
        row1.add(new KeyboardButton("🔮 Графік на завтра"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📊 Аналітика"));
        row2.add(new KeyboardButton("⚙️ Налаштування"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("💬 Підтримка"));

        if (chatId == 5450073627L) {
            row3.add(new KeyboardButton("🔐 Адмін-панель"));
        }

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void sendStartMenu(long chatId) {
        try {
            DatabaseService.registerUser(chatId);
        } catch (SQLException e) { e.printStackTrace(); }

        // Send a dummy message to activate the main menu keyboard first
        sendMessage(chatId, "Вітаємо! Для початку роботи оберіть вашу область.");


        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("👇 **Оберіть вашу область:**")
                .parseMode("Markdown")
                .replyMarkup(createRegionKeyboard())
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createRegionKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        String[] regions = {
                "Вінницька", "Волинська", "Дніпропетровська", "Донецька",
                "Житомирська", "Закарпатська", "Запорізька", "Івано-Франківська",
                "Київська", "Кіровоградська", "Луганська", "Львівська",
                "Миколаївська", "Одеська", "Полтавська", "Рівненська",
                "Сумська", "Тернопільська", "Харківська", "Херсонська",
                "Хмельницька", "Черкаська", "Чернівецька", "Чернігівська",
                "Київ"
        };

        for (int i = 0; i < regions.length; i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(InlineKeyboardButton.builder().text(regions[i]).callbackData("reg:" + regions[i]).build());
            if (i + 1 < regions.length) {
                row.add(InlineKeyboardButton.builder().text(regions[i + 1]).callbackData("reg:" + regions[i + 1])
                        .build());
            }
            rows.add(row);
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void handleCallback(long chatId, int messageId, String data) {
        try {
            if (data.startsWith("reg:")) {
                String region = data.split(":")[1];
                editMessage(chatId, messageId, "👥 **Оберіть вашу чергу (" + region + "):**",
                        createQueueKeyboard(region));
            } else if (data.equals("set:location") || data.equals("reg:back")) {
                editMessage(chatId, messageId, "👇 **Оберіть вашу область:**", createRegionKeyboard());
            } else if (data.equals("set:notifs")) {
                UserSetting s = DatabaseService.getUserSettings(chatId);
                editMessage(chatId, messageId, "🔔 **Налаштування сповіщень**\n\nУвімкніть або вимкніть повідомлення:",
                        createNotifsKeyboard(s));
            } else if (data.equals("set:style")) {
                UserSetting s = DatabaseService.getUserSettings(chatId);
                editMessage(chatId, messageId, "🎨 **Вигляд графіку**\n\nЩо показувати в повідомленні?",
                        createStyleKeyboard(s));
            } else if (data.startsWith("style:")) {
                String mode = data.split(":")[1];
                DatabaseService.updateUserSetting(chatId, "display_mode", mode);
                UserSetting s = DatabaseService.getUserSettings(chatId);
                editMessage(chatId, messageId, "🎨 **Вигляд графіку**\n\nЩо показувати в повідомленні?",
                        createStyleKeyboard(s));
            } else if (data.startsWith("toggle:")) {
                String key = data.split(":")[1];
                UserSetting s = DatabaseService.getUserSettings(chatId);
                if (key.equals("outage")) {
                    DatabaseService.updateUserSetting(chatId, "notify_outage", !s.isNotifyOutage());
                } else if (key.equals("return")) {
                    DatabaseService.updateUserSetting(chatId, "notify_return", !s.isNotifyReturn());
                } else if (key.equals("changes")) {
                    DatabaseService.updateUserSetting(chatId, "notify_changes", !s.isNotifyChanges());
                }
                s = DatabaseService.getUserSettings(chatId);
                editMessage(chatId, messageId, "🔔 **Налаштування сповіщень**\n\nУвімкніть або вимкніть повідомлення:",
                        createNotifsKeyboard(s));
            } else if (data.equals("set:timers")) {
                editMessage(chatId, messageId, "⏰ **Налаштування часу**\n\nЯкий таймер ви хочете змінити?",
                        createTimersSelectKeyboard());
            } else if (data.startsWith("timer:")) {
                String type = data.split(":")[1];
                UserSetting s = DatabaseService.getUserSettings(chatId);
                if (type.equals("outage")) {
                    editMessage(chatId, messageId,
                            "🔦 **Попередження про ВІДКЛЮЧЕННЯ**\n\nЗа скільки хвилин вас попередити?",
                            createTimeSettingsKeyboard(s, true));
                } else {
                    editMessage(chatId, messageId,
                            "💡 **Попередження про ВКЛЮЧЕННЯ**\n\nЗа скільки хвилин вас попередити?",
                            createTimeSettingsKeyboard(s, false));
                }
            } else if (data.startsWith("time:")) {
                String[] parts = data.split(":");
                String type = parts[1];
                int mins = Integer.parseInt(parts[2]);
                DatabaseService.updateUserSetting(chatId,
                        type.equals("outage") ? "notify_before" : "notify_return_before", mins);
                UserSetting s = DatabaseService.getUserSettings(chatId);
                if (type.equals("outage")) {
                    editMessage(chatId, messageId,
                            "🔦 **Попередження про ВІДКЛЮЧЕННЯ**\n\nЗа скільки хвилин вас попередити?",
                            createTimeSettingsKeyboard(s, true));
                } else {
                    editMessage(chatId, messageId,
                            "💡 **Попередження про ВКЛЮЧЕННЯ**\n\nЗа скільки хвилин вас попередити?",
                            createTimeSettingsKeyboard(s, false));
                }
            } else if (data.equals("settings:back")) {
                sendSettingsMenu(chatId, messageId);
            } else if (data.equals("admin:panel")) {
                editMessage(chatId, messageId, "🔐 **Адмін-панель**\n\nОберіть дію:", createAdminPanelKeyboard());
            } else if (data.equals("admin:stats")) {
                int totalUsers = DatabaseService.getTotalUsersCount();
                int unsubscribed = DatabaseService.getUnsubscribedUsersCount();
                int totalGroups = DatabaseService.getGroupSubsCount();
                int activeTickets = DatabaseService.getActiveTickets().size();
                String statsText = "📊 **Загальна статистика бота**\n\n" +
                        "👥 Всього користувачів: " + totalUsers + "\n" +
                        "🚫 Без підписки: " + unsubscribed + "\n" +
                        "🎫 Активні тікети: " + activeTickets + "\n\n" +
                        "🕒 Останнє оновлення: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                editMessage(chatId, messageId, statsText, createAdminPanelKeyboard());

            } else if (data.equals("admin:tickets")) {
                sendActiveTicketsList(chatId, messageId);
            } else if (data.startsWith("ticket:close:")) {
                int ticketId = Integer.parseInt(data.split(":")[2]);
                DatabaseService.closeTicket(ticketId);
                sendActiveTicketsList(chatId, messageId);
            } else if (data.startsWith("ticket:close_user:")) {
                long userId = Long.parseLong(data.split(":")[2]);
                DatabaseService.closeTicketByUserId(userId);
                editMessage(chatId, messageId, "✅ Тікет користувача " + userId + " закрито.",
                        createAdminPanelKeyboard());
            } else if (data.equals("admin:broadcast")) {

                userStates.put(chatId, "AWAITING_BROADCAST");
                editMessage(chatId, messageId,
                        "📢 **Розсилка повідомлень**\n\nНадішліть текст повідомлення, яке отримають всі активні користувачі.\n\nДля скасування напишіть `скасувати`.",
                        null);
            } else if (data.startsWith("reply:")) {
                String targetId = data.split(":")[1];
                userStates.put(chatId, "AWAITING_REPLY:" + targetId);
                sendMessage(chatId, "✍️ **Введіть вашу відповідь для користувача** [" + targetId + "](tg://user?id="
                        + targetId + "):\n\nДля скасування напишіть `скасувати`.");
            } else if (data.startsWith("queue:")) {
                String[] parts = data.split(":");
                String region = parts[1];
                String queue = parts[2];
                DatabaseService.saveUser(chatId, region, queue);
                editMessage(chatId, messageId,
                        "✅ **Успішно!**\nВи підписані на регіон **" + region + "**, черга **" + queue + "**.", null);
                sendMessage(chatId, "Ви можете переглянути графік або змінити налаштування за допомогою кнопок нижче:");
            } else if (data.equals("menu:close")) {
                deleteMessage(chatId, messageId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createQueueKeyboard(String regionName) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        try {
            com.bot.model.ScheduleResponse data = ApiService.fetchApiData();
            if (data != null && data.getRegions() != null) {
                com.bot.model.Region region = data.getRegions().stream()
                        .filter(r -> r.getNameUa().equalsIgnoreCase(regionName))
                        .findFirst().orElse(null);

                if (region != null && region.getSchedule() != null) {
                    List<String> queues = new ArrayList<>(region.getSchedule().keySet());
                    Collections.sort(queues, (a, b) -> {
                        try {
                            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
                        } catch (Exception e) {
                            return a.compareTo(b);
                        }
                    });

                    for (int i = 0; i < queues.size(); i += 3) {
                        List<InlineKeyboardButton> row = new ArrayList<>();
                        for (int j = 0; j < 3 && (i + j) < queues.size(); j++) {
                            String q = queues.get(i + j);
                            row.add(InlineKeyboardButton.builder().text(q).callbackData("queue:" + regionName + ":" + q)
                                    .build());
                        }
                        rows.add(row);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (rows.isEmpty()) {
            // Fallback to 1-6 if API fails or no data
            for (int i = 1; i <= 6; i++) {
                String q = String.valueOf(i);
                rows.add(List.of(InlineKeyboardButton.builder().text("Черга " + q)
                        .callbackData("queue:" + regionName + ":" + q).build()));
            }
        }

        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("reg:back").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void sendSettingsMenu(long chatId) {
        sendSettingsMenu(chatId, -1);
    }

    private void sendSettingsMenu(long chatId, int messageId) {
        try {
            UserSetting s = DatabaseService.getUserSettings(chatId);
            if (s == null) {
                sendMessage(chatId, "❌ Спочатку виберіть регіон та чергу за допомогою /start");
                return;
            }

            String text = "⚙️ **Головні налаштування**\n" +
                    "📍 Локація: " + s.getRegion() + ", Черга " + s.getQueue() + "\n\n" +
                    "⏰ Таймер відключення: " + s.getNotifyBefore() + " хв\n" +
                    "⏰ Таймер включення: "
                    + (s.getNotifyReturnBefore() > 0 ? s.getNotifyReturnBefore() + " хв" : "Вимкнено") + "\n" +
                    "🎨 Вигляд графіку: " + (s.getDisplayMode().equals("light") ? "🟢 Показую, коли світло Є"
                            : "⬛ Показую, коли світла НЕМАЄ");

            if (messageId == -1) {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .parseMode("Markdown")
                        .replyMarkup(createSettingsKeyboard(chatId))
                        .build();
                execute(message);
            } else {
                editMessage(chatId, messageId, text, createSettingsKeyboard(chatId));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup createNotifsKeyboard(UserSetting s) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(InlineKeyboardButton.builder().text((s.isNotifyOutage() ? "✅ " : "") + "Коли зникає світло")
                .callbackData("toggle:outage").build()));
        rows.add(List
                .of(InlineKeyboardButton.builder().text((s.isNotifyReturn() ? "✅ " : "") + "Коли з'являється світло")
                        .callbackData("toggle:return").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text((s.isNotifyChanges() ? "✅ " : "") + "Якщо змінився графік")
                .callbackData("toggle:changes").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("settings:back").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup createTimersSelectKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                InlineKeyboardButton.builder().text("🔦 Відключення").callbackData("timer:outage").build(),
                InlineKeyboardButton.builder().text("💡 До включення").callbackData("timer:return").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("settings:back").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup createTimeSettingsKeyboard(UserSetting s, boolean isOutage) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int current = isOutage ? s.getNotifyBefore() : s.getNotifyReturnBefore();
        String prefix = isOutage ? "time:outage:" : "time:return:";

        int[] times = { 5, 15, 30, 60 };
        for (int i = 0; i < times.length; i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String text1 = (times[i] == 60) ? "1 год" : times[i] + " хв";
            if (current == times[i])
                text1 = "✅ " + text1;
            row.add(InlineKeyboardButton.builder().text(text1).callbackData(prefix + times[i]).build());

            String text2 = (times[i + 1] == 60) ? "1 год" : times[i + 1] + " хв";
            if (current == times[i + 1])
                text2 = "✅ " + text2;
            row.add(InlineKeyboardButton.builder().text(text2).callbackData(prefix + times[i + 1]).build());
            rows.add(row);
        }

        String noneText = "🔕 Не нагадувати";
        if (current == 0)
            noneText = "✅ " + noneText;
        rows.add(List.of(InlineKeyboardButton.builder().text(noneText).callbackData(prefix + "0").build()));

        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("set:timers").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup createStyleKeyboard(UserSetting s) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        boolean isLight = "light".equals(s.getDisplayMode());
        rows.add(List.of(InlineKeyboardButton.builder()
                .text((!isLight ? "✅ " : "") + "⬛ Коли світла НЕМАЄ")
                .callbackData("style:blackout").build()));
        rows.add(List.of(InlineKeyboardButton.builder()
                .text((isLight ? "✅ " : "") + "🟢 Коли світло Є")
                .callbackData("style:light").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("settings:back").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup createSettingsKeyboard(long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List
                .of(InlineKeyboardButton.builder().text("⏰ Налаштувати таймери >").callbackData("set:timers").build()));
        rows.add(List.of(
                InlineKeyboardButton.builder().text("🔔 Налаштування сповіщень >").callbackData("set:notifs").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("🎨 Вигляд графіку >").callbackData("set:style").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("📍 Змінити область/чергу >").callbackData("set:location")
                .build()));

        rows.add(List.of(InlineKeyboardButton.builder().text("❌ Закрити меню").callbackData("menu:close").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup createAdminPanelKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(
                List.of(InlineKeyboardButton.builder().text("📊 Статистика бота").callbackData("admin:stats").build()));
        rows.add(List
                .of(InlineKeyboardButton.builder().text("🎫 Активні тікети").callbackData("admin:tickets").build()));
        rows.add(List
                .of(InlineKeyboardButton.builder().text("📢 Розсилка всім").callbackData("admin:broadcast").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("settings:back").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void sendActiveTicketsList(long adminId, int messageId) {
        try {
            List<String[]> tickets = DatabaseService.getActiveTickets();
            if (tickets.isEmpty()) {
                editMessage(adminId, messageId, "✅ **Активних тікетів немає!**", createAdminPanelKeyboard());
                return;
            }

            StringBuilder sb = new StringBuilder("🎫 **Список активних тікетів:**\n\n");
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            for (String[] t : tickets) {
                String tid = t[0];
                String uid = t[1];
                String status = t[3];
                String fname = t[4];
                String uname = (t[2] != null) ? " (@" + t[2] + ")" : "";
                
                String displayName = (fname != null) ? fname : "User " + uid;
                sb.append("🔹 #").append(tid).append(" - ").append(displayName).append(uname).append("\n");

                rows.add(List.of(
                        InlineKeyboardButton.builder().text("✍️ Відп #" + tid).callbackData("reply:" + uid).build(),
                        InlineKeyboardButton.builder().text("✅ Закрити").callbackData("ticket:close:" + tid).build()));
            }


            rows.add(List
                    .of(InlineKeyboardButton.builder().text("⬅️ Назад до панелі").callbackData("admin:panel").build()));
            editMessage(adminId, messageId, sb.toString(), InlineKeyboardMarkup.builder().keyboard(rows).build());
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(adminId, "❌ Помилка бази даних при читанні тікетів.");
        }
    }

    private void handleBroadcastMessage(long chatId, String text) {
        userStates.remove(chatId);
        if (text.equalsIgnoreCase("скасувати")) {
            sendMessage(chatId, "❌ Розсилку скасовано.");
            return;
        }

        try {
            List<Long> allUsers = DatabaseService.getAllUserIds();
            int count = 0;
            for (Long userId : allUsers) {
                try {
                    sendDirectMessage(userId, text);
                    count++;
                } catch (Exception e) {
                    // Skip users who blocked the bot
                }
            }
            sendMessage(chatId, "✅ Розсилку завершено! Надіслано " + count + " користувачам.");
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Помилка при отриманні списку користувачів.");
        }
    }

    private void sendStats(long chatId) {
        try {
            UserSetting s = DatabaseService.getUserSettings(chatId);
            if (s == null) {
                sendMessage(chatId, "❌ Спочатку виберіть регіон та чергу за допомогою /start");
                return;
            }

            List<String[]> stats = DatabaseService.getStatsData(s.getRegion(), s.getQueue());
            if (stats.isEmpty()) {
                sendMessage(chatId, "📊 **Аналітика за 7 днів порожня.**\nДані збираються поступово.");
                return;
            }

            StringBuilder sb = new StringBuilder("📊 **Аналітика відключень (7 днів)**\n\n");
            for (String[] row : stats) {
                sb.append("📅 ").append(row[0]).append(": **").append(row[1]).append(" год**\n");
            }
            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSupportMenu(long chatId) {
        userStates.put(chatId, "AWAITING_SUPPORT");
        sendMessage(chatId,
                "💬 **Служба підтримки**\n\nНапишіть ваше повідомлення, і адміністратор відповість вам найближчим часом.\n\nДля скасування просто оберіть інший пункт меню.");
    }

    private void handleSupportMessage(long chatId, String text, String username, String fullName) {
        userStates.remove(chatId);
        // If user typed a command, don't treat it as a support message
        if (text.startsWith("/") || text.contains("⚙️") || text.contains("📅") || text.contains("🔮")
                || text.contains("📊") || text.contains("💬")) {
            handleCommand(chatId, text, username, fullName);
            return;
        }

        try {
            DatabaseService.createOrUpdateTicket(chatId, username, fullName);
        } catch (SQLException e) {
            e.printStackTrace();
        }


        String userDisplay = "[" + fullName + "](tg://user?id=" + chatId + ")";
        if (username != null && !username.isEmpty()) {
            userDisplay += " (@" + username + ")";
        }

        String alertText = "🆘 **Нове звернення в підтримку!**\n" +
                "👤 Від: " + userDisplay + "\n" +
                "📝 Повідомлення: " + text;

        InlineKeyboardMarkup replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("✍️ Відповісти").callbackData("reply:" + chatId).build(),
                        InlineKeyboardButton.builder().text("✅ Закрити").callbackData("ticket:close_user:" + chatId)
                                .build()))
                .build();

        for (Long adminId : Config.ADMIN_IDS) {
            SendMessage message = SendMessage.builder()
                    .chatId(adminId)
                    .text(alertText)
                    .parseMode("Markdown")
                    .replyMarkup(replyMarkup)
                    .build();
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        sendMessage(chatId, "✅ Ваше повідомлення надіслано адміністратору. Очікуйте на відповідь.");
    }

    private void handleAdminReply(long adminId, String text, String targetUserIdStr) {
        userStates.remove(adminId);
        long targetUserId = Long.parseLong(targetUserIdStr);

        if (text.equalsIgnoreCase("скасувати")) {
            sendMessage(adminId, "❌ Відповідь скасовано.");
            return;
        }

        sendDirectMessage(targetUserId, "💬 **Відповідь адміністратора:**\n\n" + text);

        InlineKeyboardMarkup backMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(InlineKeyboardButton.builder().text("🎫 До списку тікетів")
                        .callbackData("admin:tickets").build()))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(adminId)
                .text("✅ Відповідь надіслано користувачу.")
                .replyMarkup(backMarkup)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendSchedule(long chatId, boolean today) {
        try {
            UserSetting s = DatabaseService.getUserSettings(chatId);
            if (s == null) {
                sendMessage(chatId, "❌ Спочатку виберіть регіон та чергу за допомогою /start");
                return;
            }

            com.bot.model.ScheduleResponse data = ApiService.fetchApiData();
            if (data == null || data.getRegions() == null) {
                sendMessage(chatId, "❌ Не вдалося отримати дані з сервера.");
                return;
            }

            LocalDateTime targetDate = today ? LocalDateTime.now() : LocalDateTime.now().plusDays(1);
            String dateKey = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String displayDate = targetDate.format(DateTimeFormatter.ofPattern("dd.MM"));
            String dayUa = getDayOfWeekUa(targetDate);

            com.bot.model.Region region = data.getRegions().stream()
                    .filter(r -> r.getNameUa().equalsIgnoreCase(s.getRegion()))
                    .findFirst().orElse(null);

            if (region == null || region.getSchedule() == null) {
                sendMessage(chatId, "❌ Дані для вашого регіону тимчасово відсутні.");
                return;
            }

            Map<String, Object> queueSch = region.getSchedule().get(s.getQueue());
            Object daySch = (queueSch != null) ? queueSch.get(dateKey) : null;

            double hoursOff = ApiService.calculateOffHours(daySch);

            if (daySch == null || (!today && hoursOff == 0)) {
                if (today)
                    sendMessage(chatId, "❌ Дані на сьогодні відсутні.");
                else
                    sendMessage(chatId, "🕒 Графік на завтра (" + displayDate + ") ще не оприлюднено.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("💡 **Графік відключень на ").append(today ? "сьогодні" : "завтра").append(", ")
                    .append(displayDate).append(" (").append(dayUa).append(")**\n");
            sb.append("👤 **Черга: ").append(s.getQueue()).append("**\n");
            sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
            sb.append(formatSchedule(daySch, s.getDisplayMode()));
            sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
            sb.append("⚡ **Гарантовано без світла: ").append((int) hoursOff).append(" год.**");

            sendMessage(chatId, sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatSchedule(Object schedule, String mode) {
        boolean lightMode = "light".equals(mode);
        String emoji = lightMode ? "🟢 " : "🔴 ";

        if (schedule instanceof List) {
            List<String> list = (List<String>) schedule;
            if (list.isEmpty())
                return lightMode ? "🔴 Відключення на весь день.\n" : "✅ Відключень не передбачено.\n";
            StringBuilder sb = new StringBuilder();
            for (String s : list)
                sb.append(emoji).append(s).append("\n");
            return sb.toString();
        } else if (schedule instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) schedule;
            List<String> intervals = new ArrayList<>();
            String start = null;
            List<String> sortedTimes = new ArrayList<>(map.keySet());
            Collections.sort(sortedTimes);

            int targetVal = lightMode ? 0 : 2; // 2 is outage, 0 is light

            for (String t : sortedTimes) {
                Object val = map.get(t);
                boolean active = (val instanceof Integer && (Integer) val == targetVal);
                if (active) {
                    if (start == null)
                        start = t;
                } else if (start != null) {
                    intervals.add(start + "-" + t);
                    start = null;
                }
            }
            if (start != null)
                intervals.add(start + "-00:00");

            if (intervals.isEmpty()) {
                return lightMode ? "🔴 Світла не буде весь день.\n" : "✅ Відключень не передбачено.\n";
            }

            StringBuilder sb = new StringBuilder();
            for (String i : intervals)
                sb.append(emoji).append(i).append("\n");
            return sb.toString();
        }
        return "⚠️ Невідомий формат даних\n";
    }

    private String getDayOfWeekUa(LocalDateTime date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Понеділок";
            case TUESDAY -> "Вівторок";
            case WEDNESDAY -> "Середа";
            case THURSDAY -> "Четвер";
            case FRIDAY -> "П'ятниця";
            case SATURDAY -> "Субота";
            case SUNDAY -> "Неділя";
        };
    }

    private void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            if (!e.getMessage().contains("message is not modified")) {
                e.printStackTrace();
            }
        }
    }

    private void deleteMessage(long chatId, int messageId) {
        org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage delete = new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage();
        delete.setChatId(chatId);
        delete.setMessageId(messageId);
        try {
            execute(delete);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, createMainMenuKeyboard(chatId));
    }

    private void sendMessage(long chatId, String text, ReplyKeyboard markup) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendDirectMessage(long userId, String text) {
        sendMessage(userId, text);
    }

    public void sendNotification(String text) {
        if (Config.CHANNEL_ID != null && !Config.CHANNEL_ID.isEmpty()) {
            SendMessage message = SendMessage.builder()
                    .chatId(Config.CHANNEL_ID)
                    .text(text)
                    .parseMode("Markdown")
                    .build();
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
