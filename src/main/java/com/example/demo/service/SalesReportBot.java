package com.example.demo.service;

import com.example.demo.config.HisobotBotConfig;
import com.example.demo.model.OrderHistory;
import com.example.demo.model.OrderStatus;
import com.example.demo.repository.OrderHistoryRepository;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
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

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    private final HisobotBotConfig config;
    private final OrderHistoryRepository orderHistoryRepository;

    public SalesReportBot(HisobotBotConfig config,OrderHistoryRepository orderHistoryRepository) {
        this.config = config;
        this.orderHistoryRepository = orderHistoryRepository;

        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/start", "botni boshlash"));
        commands.add(new BotCommand("/yordam", "yordam olish"));

        try {
            this.execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, LocalDate> userInputDates = new HashMap<>();


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (message) {
                case "/start", "Start" -> sendWelcomeMessage(chatId);
                case "/yordam", "Yordam" -> sendMessage(chatId, "Shulardan foydalaning\n" +
                        "/start - Botni boshlash\n"+
                        "/Hisobot_olish - Kunlik/Haftalik/Oylik hisobotlar\n" +
                        "/Sana_boyicha_hisobot - Sana kiriting va kerakli Hisobotlarni oling\n" +
                        "/Oldingi_oyning_hisobi - O'tgan oyning hisobotlari\n" +
                        "/Oldingi_haftaning_hisobi - O'tgan haftaning hisobotlari\n" +
                        "/barchasini_tozalash - menudagi barcha malumotlarni o'chirish");
                case "/Hisobot_olish" -> sendReportOptions(chatId);
                case "/Sana_boyicha_hisobot" -> showDateSelectionOptions(chatId);
                case "/Oldingi_oyning_hisobi" -> showPreviousMonthReportOptions(chatId);
                case "/Oldingi_haftaning_hisobi" -> showPreviousWeekReportOptions(chatId);
                default -> sendMessage(chatId, "Iltimos, mavjud buyruqlardan birini tanlang: /start ni bosing");
            }
            if (userState.containsKey(chatId)) {
                handleCustomDateSelectionSold(chatId, message);
                handleCustomDateSelectionCanceled(chatId, message);
                handleCustomDateSelectionOrdered(chatId, message);
                return;
            }
        }
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            System.out.println("Bosilgan tugma: " + callbackData);

            if (callbackData.startsWith("PHONE_")) {
                if (callbackData.length() > 6) {
                    String phoneNumber1 = callbackData.substring(6);
                    sendUserOrderHistory(chatId, phoneNumber1);
                }
            }

            switch (callbackData) {
                case "GET_REPORT" -> sendReportOptions(chatId);

                case "DAILY_REPORT" -> sendDailyOptions(chatId); //sendSalesReport(chatId, LocalDate.now(), LocalDate.now());
                case "SOLD_ITEMS_DAILY" -> sendSoldReport(chatId, LocalDate.now(), LocalDate.now());
                case "ORDERED_ITEMS_DAILY" -> sendOrderedReport(chatId, LocalDate.now(), LocalDate.now());
                case "CANCELED_ITEMS_DAILY" -> sendCancelReport(chatId, LocalDate.now(), LocalDate.now());

                case "WEEKLY_REPORT" -> sendWeeklyOptions(chatId);
                case "SOLD_ITEMS_WEEK" -> {
                    LocalDate startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY);
                    sendSoldReport(chatId, startOfWeek, LocalDate.now());
                }
                case "ORDERED_ITEMS_WEEK" -> {
                    LocalDate startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY);
                    sendOrderedReport(chatId, startOfWeek, LocalDate.now());
                }
                case "CANCELED_ITEMS_WEEK" -> {
                    LocalDate startOfWeek = LocalDate.now().with(DayOfWeek.MONDAY);
                    sendCancelReport(chatId, startOfWeek, LocalDate.now());
                }

                case "MONTHLY_REPORT" -> sendMonthlyOptions(chatId);
                case "MONTHLY_REPORT_SOLD" -> {
                    LocalDate startOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
                    sendSoldReport(chatId, startOfMonth, LocalDate.now());
                }
                case "MONTHLY_REPORT_CANCELED" -> {
                    LocalDate startOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
                    sendCancelReport(chatId, startOfMonth, LocalDate.now());
                }
                case "MONTHLY_REPORT_ORDERED" -> {
                    LocalDate startOfMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
                    sendOrderedReport(chatId, startOfMonth, LocalDate.now());
                }

                case "CUSTOM_DATE_REPORT" -> showDateSelectionOptions(chatId);
                case "CUSTOM_DATE_REPORT_SOLD" -> showDateSelectionSold(chatId);
                case "CUSTOM_DATE_REPORT_CANCELED" ->showDateSelectionCanceled(chatId);
                case "CUSTOM_DATE_REPORT_ORDERED" -> showDateSelectionOrdered(chatId);


                case "LAST_MONTH_REPORT" -> showPreviousMonthReportOptions(chatId);
                case "LAST_MONTH_REPORT_SOLD" -> showPreviousMonthReportSold(chatId);
                case "LAST_MONTH_REPORT_CANCELED" -> showPreviousMonthReportCanceled(chatId);
                case "LAST_MONTH_REPORT_ORDERED" -> showPreviousMonthReportOrdered(chatId);

                case "LAST_WEEK_REPORT" -> showPreviousWeekReportOptions(chatId);
                case "LAST_WEEK_REPORT_SOLD" -> showPreviousWeekReportSold(chatId);
                case "LAST_WEEK_REPORT_ORDERED" -> showPreviousWeekReportOrdered(chatId);
                case "LAST_WEEK_REPORT_CANCELED" -> showPreviousWeekReportCanceled(chatId);


                case "ORDERED_REPORT" -> sendOrderReport(chatId, OrderStatus.ORDERED);
                case "CANCELED_REPORT", "BACK_TO_CANCELED_NUMBERS" -> sendCanceledOrders(chatId);

                case "BACK_TO_CANCELED_TO_WELCOME","BACK_TO_CANCELED" -> sendWelcomeMessage(chatId);
            }
        }
    }


    private void sendWelcomeMessage(long chatId) {
        String text = "Assalomu alaykum! \nHush kelibsiz! Nimani ko‘rishni hohlaysiz?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row1 = new ArrayList<>();
        row1.add(List.of(createButton("📊 Kun/Oy hisobotlari", "GET_REPORT")));
        row1.add(List.of(createButton("🟡 ORDERED qilingan buyurtmalar\n(oxirgi 2 haftalik)", "ORDERED_REPORT")));
        row1.add(List.of(createButton("🔴 CANCELED qilingan buyurtmalar\n(oxirgi 1 haftalik)", "CANCELED_REPORT")));

        markup.setKeyboard(row1);

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
        rows.add(List.of(createButton("📆 O‘tgan oyning hisobi", "LAST_MONTH_REPORT")));
        rows.add(List.of(createButton("📊 O‘tgan haftaning hisobi", "LAST_WEEK_REPORT")));
        rows.add(List.of(createButton("📅 Sana bo‘yicha hisobot", "CUSTOM_DATE_REPORT")));

        markup.setKeyboard(rows);
        sendMessageWithMarkup(chatId, text, markup);
    }

    private void sendCanceledOrders(long chatId) {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        List<OrderHistory> canceledOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                OrderStatus.CANCELED, oneWeekAgo, LocalDateTime.now());

        if (canceledOrders.isEmpty()) {
            sendMessage(chatId, "Oxirgi 1 haftada CANCELED bo'lgan buyurtmalar yo‘q.");
            return;
        }

        Map<String, String> phoneOrders = new HashMap<>();
        for (OrderHistory order : canceledOrders) {
            phoneOrders.put(order.getClient().getPhoneNumber(), order.getClient().getName());
        }

        String text = "📞 Oxirgi 1 hafta ichida buyurtmasini CANCELED qilgan mijozlar:\n\n";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Map.Entry<String, String> entry : phoneOrders.entrySet()) {
            rows.add(List.of(createButton(entry.getValue() + " - " + entry.getKey(), "PHONE_" + entry.getKey())));
        }
        rows.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED_TO_WELCOME")));
        markup.setKeyboard(rows);
        sendMessageWithMarkup(chatId, text, markup);
    }
    private void sendUserOrderHistory(long chatId, String phoneNumber) {
        LocalDateTime twoWeeksAgo = LocalDateTime.now().minusWeeks(2);
        List<OrderHistory> soldOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(OrderStatus.SOLD, twoWeeksAgo, LocalDateTime.now());
        List<OrderHistory> canceledOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(OrderStatus.CANCELED, twoWeeksAgo, LocalDateTime.now());
        List<OrderHistory> orderedOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(OrderStatus.ORDERED, twoWeeksAgo, LocalDateTime.now());

        List<OrderHistory> userOrders = new ArrayList<>();
        userOrders.addAll(soldOrders);
        userOrders.addAll(canceledOrders);
        userOrders.addAll(orderedOrders);

        userOrders.removeIf(order -> !order.getClient().getPhoneNumber().equals(phoneNumber));

        if (userOrders.isEmpty()) {
            sendMessage(chatId, "Ushbu mijoz oxirgi 2 haftada hech qanday buyurtma bermagan.");
            return;
        }
        StringBuilder report = new StringBuilder("📜 " + phoneNumber + " oxirgi 2 haftalik buyurtmalari:\n\n");

        for (OrderHistory order : userOrders) {
            String statusTag="";

            if(order.getStatus() == OrderStatus.SOLD){
                statusTag = "🟢 Sotilgan Buyurtma";
            } else if(order.getStatus() == OrderStatus.CANCELED){
                statusTag = "🔴 Bekor qilingan Buyurtma";
            } else if (order.getStatus() == OrderStatus.ORDERED){
                statusTag = "ORDERED qilingan Buyurtma";
            }

            report.append(statusTag).append("\n")
                    .append("🆔 Buyurtma: ").append(order.getOrderIndex()).append("\n")
                    .append("👤 *Ism:* ").append(order.getClient().getName()).append("\n")
                    .append("📞 *Telefon:* ").append(order.getClient().getPhoneNumber()).append("\n")
                    .append("⏳ Olib ketish vaqti: ").append(order.getClient().getPickupTime()).append("\n\n")
                    .append("💰 Jami narx: ").append(order.getTotalPrice()).append(" so'm\n")
                    .append("------------------\n");
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED_NUMBERS"))));
        sendMessageWithMarkup(chatId, report.toString(), markup);
    }

    private void sendOrderReport(long chatId, OrderStatus status) {
        List<OrderHistory> orders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                status, LocalDate.now().minusWeeks(2).atStartOfDay(), LocalDate.now().atTime(23, 59, 59));
        if (orders.isEmpty()) {
            sendMessage(chatId, "ORDERED bo'lgan buyurtmalar yo‘q. Barcha buyurtma qilingan maxsulotlar SOLD yoki CANCELED deb belgilangan!");
            return;
        }
        StringBuilder report = new StringBuilder("📊 " + status + " buyurtmalar:\n\n");
        for (OrderHistory order : orders) {
            report.append("Orxirgi 2 haftalikdagi ORDERED bo'lgan lekin haligacha sotib olinmagan buyurtmalar\n")
                    .append(order.getOrderDetails()).append("\n------------------\n");
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED"))));

        sendMessageWithMarkup(chatId, report.toString(), markup);
    }


    private void sendDailyOptions(long chatId) {
        String text1 = "Nimani ko'rmoqchisiz?";
        InlineKeyboardMarkup markup1 = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row2 = new ArrayList<>();
        row2.add(List.of(createButton("📊 Sotilgan maxsulotlar", "SOLD_ITEMS_DAILY")));
        row2.add(List.of(createButton("🟡 Buyurtma qilingan", "ORDERED_ITEMS_DAILY")));
        row2.add(List.of(createButton("🔴 Bekor qilingan maxsulotlar", "CANCELED_ITEMS_DAILY")));

        markup1.setKeyboard(row2);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text1);
        message.setReplyMarkup(markup1);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void sendWeeklyOptions(long chatId) {
        String text1 = "Nimani ko'rmoqchisiz?";
        InlineKeyboardMarkup markup1 = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row2 = new ArrayList<>();
        row2.add(List.of(createButton("📊 Sotilgan maxsulotlar", "SOLD_ITEMS_WEEK")));
        row2.add(List.of(createButton("🟡 Buyurtma qilingan", "ORDERED_ITEMS_WEEK")));
        row2.add(List.of(createButton("🔴 Bekor qilingan maxsulotlar", "CANCELED_ITEMS_WEEK")));

        markup1.setKeyboard(row2);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text1);
        message.setReplyMarkup(markup1);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void sendMonthlyOptions(long chatId) {
        String text1 = "Nimani ko'rmoqchisiz?";
        InlineKeyboardMarkup markup1 = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row2 = new ArrayList<>();
        row2.add(List.of(createButton("📊 Sotilgan maxsulotlar", "MONTHLY_REPORT_SOLD")));
        row2.add(List.of(createButton("🟡 Buyurtma qilingan", "MONTHLY_REPORT_ORDERED")));
        row2.add(List.of(createButton("🔴 Bekor qilingan maxsulotlar", "MONTHLY_REPORT_CANCELED")));

        markup1.setKeyboard(row2);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text1);
        message.setReplyMarkup(markup1);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void showPreviousWeekReportOptions(long chatId) {
        String text1 = "Nimani ko'rmoqchisiz?";
        InlineKeyboardMarkup markup1 = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row2 = new ArrayList<>();
        row2.add(List.of(createButton("📊 Sotilgan maxsulotlar", "LAST_WEEK_REPORT_SOLD")));
        row2.add(List.of(createButton("🟡 Buyurtma qilingan", "LAST_WEEK_REPORT_ORDERED")));
        row2.add(List.of(createButton("🔴 Bekor qilingan maxsulotlar", "LAST_WEEK_REPORT_CANCELED")));

        markup1.setKeyboard(row2);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text1);
        message.setReplyMarkup(markup1);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void showPreviousWeekReportSold(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfLastWeek = today.minusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate endOfLastWeek = today.minusWeeks(1).with(DayOfWeek.SUNDAY);
        sendSoldReport(chatId, startOfLastWeek, endOfLastWeek);
    }
    private void showPreviousWeekReportOrdered(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfLastWeek = today.minusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate endOfLastWeek = today.minusWeeks(1).with(DayOfWeek.SUNDAY);
        sendOrderedReport(chatId, startOfLastWeek, endOfLastWeek);
    }
    private void showPreviousWeekReportCanceled(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfLastWeek = today.minusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate endOfLastWeek = today.minusWeeks(1).with(DayOfWeek.SUNDAY);
        sendCancelReport(chatId, startOfLastWeek, endOfLastWeek);
    }


    private void showPreviousMonthReportOptions(long chatId) {
        String text1 = "Nimani ko'rmoqchisiz?";
        InlineKeyboardMarkup markup1 = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row2 = new ArrayList<>();
        row2.add(List.of(createButton("📊 Sotilgan maxsulotlar", "LAST_MONTH_REPORT_SOLD")));
        row2.add(List.of(createButton("🟡 Buyurtma qilingan", "LAST_MONTH_REPORT_ORDERED")));
        row2.add(List.of(createButton("🔴 Bekor qilingan maxsulotlar", "LAST_MONTH_REPORT_CANCELED")));

        markup1.setKeyboard(row2);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text1);
        message.setReplyMarkup(markup1);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void showPreviousMonthReportSold(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        sendSoldReport(chatId, firstDayOfLastMonth, lastDayOfLastMonth);
    }
    private void showPreviousMonthReportCanceled(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        sendCancelReport(chatId, firstDayOfLastMonth, lastDayOfLastMonth);
    }
    private void showPreviousMonthReportOrdered(long chatId) {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        sendOrderedReport(chatId, firstDayOfLastMonth, lastDayOfLastMonth);
    }


    private void showDateSelectionOptions(long chatId) {
        String text1 = "Sanalar orasida nimani ko'rmoqchisiz?";
        InlineKeyboardMarkup markup1 = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row2 = new ArrayList<>();
        row2.add(List.of(createButton("📊 Sotilgan maxsulotlar", "CUSTOM_DATE_REPORT_SOLD")));
        row2.add(List.of(createButton("🟡 Buyurtma qilingan", "CUSTOM_DATE_REPORT_ORDERED")));
        row2.add(List.of(createButton("🔴 Bekor qilingan maxsulotlar", "CUSTOM_DATE_REPORT_CANCELED")));

        markup1.setKeyboard(row2);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text1);
        message.setReplyMarkup(markup1);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void showDateSelectionSold(long chatId) {
        sendMessage(chatId, "📅 Qaysi sanalardagi hisobotlarni ko‘rmoqchisiz?\n\nIltimos, boshlanish sanasini kiriting (kun/oy/yil formatida). Napr: 01/03/2025 | Ortga qaytish uchun /start yoki /yordam ni bosing");
        userState.put(chatId, "AWAITING_START_DATE_SOLD");
    }
    private void showDateSelectionOrdered(long chatId) {
        sendMessage(chatId, "📅 Qaysi sanalardagi hisobotlarni ko‘rmoqchisiz?\n\nIltimos, boshlanish sanasini kiriting (kun/oy/yil formatida). Napr: 01/03/2025 | Ortga qaytish uchun /start yoki /yordam ni bosing");
        userState.put(chatId, "AWAITING_START_DATE_ORDERED");
    }
    private void showDateSelectionCanceled(long chatId) {
        sendMessage(chatId, "📅 Qaysi sanalardagi hisobotlarni ko‘rmoqchisiz?\n\nIltimos, boshlanish sanasini kiriting (kun/oy/yil formatida). Napr: 01/03/2025 | Ortga qaytish uchun /start yoki /yordam ni bosing");
        userState.put(chatId, "AWAITING_START_DATE_CANCELED");
    }
    private void handleCustomDateSelectionSold(long chatId, String text) {
        if (userState.get(chatId).equals("AWAITING_START_DATE_SOLD")) {
            userInputDates.put(chatId, LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sendMessage(chatId, "📅 Endi qayergacha bo‘lgan sanani ko‘rmoqchisiz? (kun/oy/yil formatida)");
            userState.put(chatId, "AWAITING_END_DATE_SOLD");
        } else if (userState.get(chatId).equals("AWAITING_END_DATE_SOLD")) {
            LocalDate startDate = userInputDates.get(chatId);
            LocalDate endDate = LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            sendSoldReport(chatId, startDate, endDate);
            userState.remove(chatId);
            userInputDates.remove(chatId);
        }
    }
    private void handleCustomDateSelectionOrdered(long chatId, String text) {
        if (userState.get(chatId).equals("AWAITING_START_DATE_ORDERED")) {
            userInputDates.put(chatId, LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sendMessage(chatId, "📅 Endi qayergacha bo‘lgan sanani ko‘rmoqchisiz? (kun/oy/yil formatida)");
            userState.put(chatId, "AWAITING_END_DATE_ORDERED");
        } else if (userState.get(chatId).equals("AWAITING_END_DATE_ORDERED")) {
            LocalDate startDate = userInputDates.get(chatId);
            LocalDate endDate = LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            sendOrderedReport(chatId, startDate, endDate);
            userState.remove(chatId);
            userInputDates.remove(chatId);
        }
    }
    private void handleCustomDateSelectionCanceled(long chatId, String text) {
        if (userState.get(chatId).equals("AWAITING_START_DATE_CANCELED")) {
            userInputDates.put(chatId, LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sendMessage(chatId, "📅 Endi qayergacha bo‘lgan sanani ko‘rmoqchisiz? (kun/oy/yil formatida)");
            userState.put(chatId, "AWAITING_END_DATE_CANCELED");
        } else if (userState.get(chatId).equals("AWAITING_END_DATE_CANCELED")) {
            LocalDate startDate = userInputDates.get(chatId);
            LocalDate endDate = LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            sendCancelReport(chatId, startDate, endDate);
            userState.remove(chatId);
            userInputDates.remove(chatId);
        }
    }


    private void sendSoldReport(long chatId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        LocalDate botStartDate = orderHistoryRepository.findEarliestOrderDate();
        if (botStartDate == null) {
            sendMessage(chatId, "📊 Hali birorta ham buyurtma mavjud emas.");
            return;
        }

        if (from.isBefore(threeMonthsAgo)) {
            sendMessage(chatId, "⚠️ Hisobot faqat oxirgi 3 oylik ma'lumotlarni ko‘rsatadi.");
            from = threeMonthsAgo;
        }

        if (from.isBefore(botStartDate)) {
            sendMessage(chatId, "⚠️ Bot " + botStartDate + " dan boshlab ishlayapti.");
            from = botStartDate;
        }

        List<OrderHistory> soldOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                OrderStatus.SOLD, from.atStartOfDay(), to.atTime(23, 59, 59));

        if (soldOrders.isEmpty()) {
            sendMessage(chatId, "📊 Ushbu davrda sotilgan mahsulotlar yo‘q.");
            return;
        }

        Map<String, Integer> productSales = new HashMap<>();
        double totalRevenue = 0;
        StringBuilder report = new StringBuilder("📊 Hisobot: " + from + " dan " + to + " gacha\n\n");
        report.append("🛒 **Sotilgan mahsulotlar:**\n");

        for (OrderHistory order : soldOrders) {
            totalRevenue += order.getTotalPrice();

            String[] items = order.getOrderDetails().split("\n");
            for (String item : items) {
                if (item.startsWith("🍔") || item.startsWith("🥗") || item.startsWith("🍕")) {
                    String[] parts = item.split(" x ");
                    if (parts.length == 2) {
                        String productName = parts[0].trim();
                        int quantity = Integer.parseInt(parts[1].split("=")[0].trim());

                        productSales.put(productName, productSales.getOrDefault(productName, 0) + quantity);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : productSales.entrySet()) {
            report.append(entry.getKey()).append(" - ").append(entry.getValue()).append(" ta\n");
        }

        report.append(String.format("\n💰 **Umumiy summa:** %.2f so‘m", totalRevenue));
        sendMessage(chatId, report.toString());
    }
    private void sendOrderedReport(long chatId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        LocalDate botStartDate = orderHistoryRepository.findEarliestOrderDate();
        if (botStartDate == null) {
            sendMessage(chatId, "📊 ORDERED bo'lgan buyurtmalar yo'q");
            return;
        }

        if (from.isBefore(threeMonthsAgo)) {
            sendMessage(chatId, "⚠️ Hisobot faqat oxirgi 3 oylikdagi ORDERED bo'lgan buyurtmalarni ko‘rsatadi.");
            from = threeMonthsAgo;
        }

        if (from.isBefore(botStartDate)) {
            sendMessage(chatId, "⚠️ Bot " + botStartDate + " dan boshlab ishlayapti.");
            from = botStartDate;
        }

        List<OrderHistory> orderedOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                OrderStatus.ORDERED, from.atStartOfDay(), to.atTime(23, 59, 59));

        if (orderedOrders.isEmpty()) {
            sendMessage(chatId, "📊 Ushbu davrda ORDERED bo'lgan buyurtmalar qolmagan");
            return;
        }

        Map<String, Integer> productSales = new HashMap<>();
        double totalRevenue = 0;
        StringBuilder report = new StringBuilder("📊 Hisobot: " + from + " dan " + to + " gacha\n\n");
        report.append("🛒 **ORDERED bo'lgan mahsulotlar:**\n");

        for (OrderHistory order : orderedOrders) {
            totalRevenue += order.getTotalPrice(); // ✅ Endi `getTotalPrice()` ishlaydi!

            String[] items = order.getOrderDetails().split("\n");
            for (String item : items) {
                if (item.startsWith("🍔") || item.startsWith("🥗") || item.startsWith("🍕")) {
                    String[] parts = item.split(" x ");
                    if (parts.length == 2) {
                        String productName = parts[0].trim();
                        int quantity = Integer.parseInt(parts[1].split("=")[0].trim());

                        productSales.put(productName, productSales.getOrDefault(productName, 0) + quantity);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : productSales.entrySet()) {
            report.append(entry.getKey()).append(" - ").append(entry.getValue()).append(" ta\n");
        }

        report.append(String.format("\n💰 **Umumiy summa:** %.2f so‘m", totalRevenue));
        sendMessage(chatId, report.toString());
    }
    private void sendCancelReport(long chatId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        LocalDate botStartDate = orderHistoryRepository.findEarliestOrderDate();
        if (botStartDate == null) {
            sendMessage(chatId, "📊 Hali birorta ham buyurtma mavjud emas.");
            return;
        }

        if (from.isBefore(threeMonthsAgo)) {
            sendMessage(chatId, "⚠️ Hisobot faqat oxirgi 3 oylikdagi CANCELED bo'lgan buyurtmalarni ko‘rsatadi.");
            from = threeMonthsAgo;
        }

        if (from.isBefore(botStartDate)) {
            sendMessage(chatId, "⚠️ Bot " + botStartDate + " dan boshlab ishlayapti.");
            from = botStartDate;
        }

        List<OrderHistory> cancelOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                OrderStatus.CANCELED, from.atStartOfDay(), to.atTime(23, 59, 59));

        if (cancelOrders.isEmpty()) {
            sendMessage(chatId, "📊 Ushbu davrda CANCELED bo'lgan mahsulotlar yo‘q.");
            return;
        }

        Map<String, Integer> productSales = new HashMap<>();
        double totalRevenue = 0;
        StringBuilder report = new StringBuilder("📊 Hisobot: " + from + " dan " + to + " gacha\n\n");
        report.append("🛒 **CANCELED bo'lgan mahsulotlar:**\n");

        for (OrderHistory order : cancelOrders) {
            totalRevenue += order.getTotalPrice();

            String[] items = order.getOrderDetails().split("\n");
            for (String item : items) {
                if (item.startsWith("🍔") || item.startsWith("🥗") || item.startsWith("🍕")) {
                    String[] parts = item.split(" x ");
                    if (parts.length == 2) {
                        String productName = parts[0].trim();
                        int quantity = Integer.parseInt(parts[1].split("=")[0].trim());

                        productSales.put(productName, productSales.getOrDefault(productName, 0) + quantity);
                    }
                }
            }
        }

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
