package com.example.demo.service;

import com.example.demo.config.HisobotBotConfig;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Orders;
import com.example.demo.repository.OrdersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SalesReportBot extends TelegramLongPollingBot {

    final HisobotBotConfig config;
    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, LocalDate> userInputDates = new HashMap<>();

    @Autowired
    private OrdersRepository ordersRepository;

    public SalesReportBot(HisobotBotConfig config) {
        this.config = config;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
             // // Group Id sini olish

        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            System.out.println("Hisobot ID: " + chatId);
        }


            if (userState.containsKey(chatId)) {
                handleCustomDateSelection(chatId, message);
                return;
            }

            switch (message) {
                case "/start":
                case "Start":
                    sendWelcomeMessage(chatId);
                    break;
                case "📊 Hisobot olish":
                    sendReportOptions(chatId);
                    break;
                case "📅 Sana bo‘yicha hisobot":
                    showDateSelection(chatId);
                    break;
                case "📆 O‘tgan oyning hisobi":
                    showPreviousMonthReport(chatId);
                    break;
                case "📊 O‘tgan haftaning hisobi":
                    showPreviousWeekReport(chatId);
                    break;
                default:
                    sendMessage(chatId, "Iltimos, mavjud buyruqlardan birini tanlang: /start yoki 📊 Hisobot olish");
            }
        }

        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            System.out.println("Bosilgan tugma: " + callbackData);

            switch (callbackData) {
                case "GET_REPORT":
                    sendReportOptions(chatId);
                    break;
                case "DAILY_REPORT":
                    sendSalesReport(chatId, LocalDate.now(), LocalDate.now());
                    break;
                case "WEEKLY_REPORT":
                    LocalDate startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY);
                    sendSalesReport(chatId, startOfWeek, LocalDate.now());
                    break;
                case "MONTHLY_REPORT":
                    LocalDate startOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
                    sendSalesReport(chatId, startOfMonth, LocalDate.now());
                    break;
                case "CUSTOM_DATE_REPORT":
                    showDateSelection(chatId);
                    break;
                case "LAST_MONTH_REPORT":
                    showPreviousMonthReport(chatId);
                    break;
                case "LAST_WEEK_REPORT":
                    showPreviousWeekReport(chatId);
                    break;
                default:
                    sendMessage(chatId, "Noma'lum buyruq!");
            }
        }
    }


    private void handleCustomDateSelection(long chatId, String text) {
        if (userState.get(chatId).equals("AWAITING_START_DATE")) {
            userInputDates.put(chatId, LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sendMessage(chatId, "📅 Endi qayergacha bo‘lgan sanani ko‘rmoqchisiz? (kun/oy/yil formatida)");
            userState.put(chatId, "AWAITING_END_DATE");
        } else if (userState.get(chatId).equals("AWAITING_END_DATE")) {
            LocalDate startDate = userInputDates.get(chatId);
            LocalDate endDate = LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            sendSalesReport(chatId, startDate, endDate);
            userState.remove(chatId);
            userInputDates.remove(chatId);
        }
    }

    private void showPreviousMonthReport(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        sendSalesReport(chatId, firstDayOfLastMonth, lastDayOfLastMonth);
    }


    private void showPreviousWeekReport(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfLastWeek = today.minusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate endOfLastWeek = today.minusWeeks(1).with(DayOfWeek.SUNDAY);
        sendSalesReport(chatId, startOfLastWeek, endOfLastWeek);
    }

    private void showDateSelection(long chatId) {
        sendMessage(chatId, "📅 Qaysi sanalardagi hisobotlarni ko‘rmoqchisiz?\n\nIltimos, boshlanish sanasini kiriting (kun/oy/yil formatida). Napr: 01/03/2025");
        userState.put(chatId, "AWAITING_START_DATE");
    }
    private void sendWelcomeMessage(long chatId) {
        String text = "Assalomu alaykum! \nHush kelibsiz! Nimani ko‘rishni hohlaysiz?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("📊 Hisobot olish", "GET_REPORT"));
        rows.add(row1);

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendReportOptions(long chatId) {
        String text = "Qaysi davr uchun hisobot olmoqchisiz?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("1️⃣ Kunlik Hisobot", "DAILY_REPORT")));
        rows.add(List.of(createButton("2️⃣ Haftalik Hisobot", "WEEKLY_REPORT")));
        rows.add(List.of(createButton("3️⃣ Oylik Hisobot", "MONTHLY_REPORT")));
        rows.add(List.of(createButton("📅 Sana bo‘yicha hisobot", "CUSTOM_DATE_REPORT")));
        rows.add(List.of(createButton("📆 O‘tgan oyning hisobi", "LAST_MONTH_REPORT")));
        rows.add(List.of(createButton("📊 O‘tgan haftaning hisobi", "LAST_WEEK_REPORT")));

        markup.setKeyboard(rows);
        sendMessageWithMarkup(chatId, text, markup);
    }

    private void sendSalesReport(long chatId, LocalDate from, LocalDate to) {
        // ✅ Hisobotga faqat SOLD bo‘lgan buyurtmalarni olamiz
        List<Orders> soldOrders = ordersRepository.findSoldOrdersBetweenDates(from.atStartOfDay(), to.atTime(23, 59, 59));

        if (soldOrders.isEmpty()) {
            sendMessage(chatId, "📊 Ushbu davrda sotilgan mahsulotlar yo‘q.");
            return;
        }

        Map<String, Integer> productSales = new HashMap<>();
        double totalRevenue = 0;

        for (Orders order : soldOrders) { // Faqat SOLD buyurtmalarni hisoblaymiz
            totalRevenue += order.getTotalPrice();

            // ✅ Buyurtma tafsilotlarini ajratib olish
            String[] items = order.getOrderDetails().split("\n");
            for (String item : items) {
                if (item.startsWith("🍔") || item.startsWith("🥗") || item.startsWith("🍕")) {
                    String[] parts = item.split(" x ");
                    if (parts.length == 2) {
                        String productName = parts[0].trim();
                        int quantity = Integer.parseInt(parts[1].split("=")[0].trim());

                        // ✅ Mahsulotlar sonini hisoblash
                        productSales.put(productName, productSales.getOrDefault(productName, 0) + quantity);
                    }
                }
            }
        }

        // ✅ Hisobotni to‘g‘ri formatda chiqaramiz
        StringBuilder report = new StringBuilder("📊 Hisobot: " + from + " dan " + to + " gacha\n\n");
        report.append("🛒 **Sotilgan mahsulotlar:**\n");

        for (Map.Entry<String, Integer> entry : productSales.entrySet()) {
            report.append(entry.getKey()).append(" - ").append(entry.getValue()).append(" ta\n");
        }
        report.append(String.format("\n💰 **Umumiy summa:** %.2f so‘m", totalRevenue));

        sendMessage(chatId, report.toString());
    }




    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageWithMarkup(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
