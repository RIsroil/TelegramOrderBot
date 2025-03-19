package com.example.demo.service;

import com.example.demo.config.HisobotBotConfig;
import com.example.demo.model.BlockedUser;
import com.example.demo.model.Client;
import com.example.demo.model.OrderHistory;
import com.example.demo.model.OrderStatus;
import com.example.demo.repository.BlockedUserRepository;
import com.example.demo.repository.ClientRepository;
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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

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
    private final BlockedUserRepository blockedUserRepository;
    private final ClientRepository clientRepository;
    private final BlockedUserRepository blockedUsersRepository;

    public SalesReportBot(BlockedUserRepository blockedUsersRepository,ClientRepository clientRepository,BlockedUserRepository blockedUserRepository,HisobotBotConfig config,OrderHistoryRepository orderHistoryRepository) {
        this.config = config;
        this.orderHistoryRepository = orderHistoryRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.clientRepository = clientRepository;
        this.blockedUsersRepository = blockedUsersRepository;
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
                        "/Oldingi_haftaning_hisobi - O'tgan haftaning hisobotlari\n"
                        );
                case "/Hisobot_olish" -> sendReportOptions(chatId);
                case "/Sana_boyicha_hisobot" -> showDateSelectionOptions(chatId);
                case "/Oldingi_oyning_hisobi" -> showPreviousMonthReportOptions(chatId);
                case "/Oldingi_haftaning_hisobi" -> showPreviousWeekReportOptions(chatId);
//                default -> sendMessage(chatId, "Iltimos, mavjud buyruqlardan birini tanlang: /start ni bosing");
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

                    // Mijozni bazadan topish
                    Optional<Client> optionalClient = clientRepository.findByPhoneNumber(phoneNumber1);
                    if (optionalClient.isPresent()) {
                        Client client = optionalClient.get(); // Optional dan Client ni olish
                        sendUserOrderHistory(chatId, phoneNumber1, client);
                    } else {
                        sendMessage(chatId, "❌ Telefon raqam bo‘yicha mijoz topilmadi.");
                    }

                }
            }

            if (callbackData.startsWith("UNBLOCK_USER_")) {
                String phoneNumber = callbackData.substring(13);
                unblockUser(chatId, phoneNumber);
                return;
            }

            if (callbackData.startsWith("CONFIRM_BLOCK_")) {
                String phoneNumber = callbackData.substring(14);
                confirmBlockUser(chatId, phoneNumber, 3);
                return;
            }

            if (callbackData.startsWith("BLOCK_USER_")) {
                String[] parts = callbackData.split("_", 4); // 4 qismga ajratamiz

                if (parts.length == 3) {
                    // Telefon raqamni olish va bloklash variantlarini chiqarish
                    String phoneNumber = parts[2];  // Telefon raqamni olish
                    showBlockOptions(chatId, phoneNumber);
                } else if (parts.length == 4) {
                    // Telefon raqam va bloklash kunini olish
                    String phoneNumber = parts[2];
                    String daysStr = parts[3];

                    try {
                        int days = Integer.parseInt(daysStr.trim());  // Kunni integer formatga o'tkazish

                        // Foydalanuvchini bazadan qidirish
                        Optional<Client> userToBlock = clientRepository.findByPhoneNumber(phoneNumber);
                        if (userToBlock.isEmpty()) {
                            sendMessage(chatId, "❌ Foydalanuvchi topilmadi.");
                            return;
                        }

                        // Foydalanuvchini bloklash
                        handleBlockUser(chatId, phoneNumber, days);

                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "❌ Noto‘g‘ri format! Kunni to‘g‘ri kiriting.");
                    }
                } else {
                    sendMessage(chatId, "❌ Noto‘g‘ri format! Qayta urinib ko‘ring.");
                }
            }

            if (callbackData.equals("SHOW_CLIENT_PHONES")) {
                sendClientPhoneNumbers(chatId);
                return;
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
                case "CANCELED_REPORT", "BACK_TO_CANCELED_NUMBERS","BACK_TO_USER_ORDERS" -> sendCanceledOrders(chatId);

                case "BACK_TO_CANCELED_TO_WELCOME", "BACK_TO_CANCELED", "BACK_TO_ADMIN_MENU","BACK_TO_MENU" -> sendWelcomeMessage(chatId);
                case "BACK_TO_CANCELED_LIST" -> sendCanceledUsersList(chatId);
                case "BLOCK_USERS" -> sendBlockedUsers(chatId);
                case "BLOCKED_USERS" -> sendUnblockedUsers(chatId);
                case "BLOCKED_USERS_LIST" -> sendBlockedUsersList(chatId);


            }
        }
    }

    private void sendWelcomeMessage(long chatId) {
        String text = "Assalomu alaykum! \nHush kelibsiz! Nimani ko‘rishni hohlaysiz?";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> row1 = new ArrayList<>();
        row1.add(List.of(createButton("📊 Kun/Oy hisobotlari", "GET_REPORT")));
        row1.add(List.of(createButton("🟡 ORDERED qilingan buyurtmalar\n(oxirgi 2 haftalik)", "ORDERED_REPORT")));
        row1.add(List.of(createButton("🔴 CANCELED qilingan buyurtmalar\n(oxirgi 2 oylik)", "CANCELED_REPORT")));
        row1.add(List.of(createButton("📞 Foydalanuvchi tarixi", "SHOW_CLIENT_PHONES")));

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
        LocalDateTime twoMonthAgo = LocalDateTime.now().minusMonths(2);
        List<OrderHistory> canceledOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                OrderStatus.CANCELED, twoMonthAgo, LocalDateTime.now());

        if (canceledOrders.isEmpty()) {
            sendMessage(chatId, "Oxirgi 2 oyda buyurtmani CANCELED qilgan buyurtmachilar yo‘q.");
            return;
        }

        Map<String, String> phoneOrders = new HashMap<>();
        for (OrderHistory order : canceledOrders) {
            phoneOrders.put(order.getClient().getPhoneNumber(), order.getClient().getName());
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        StringBuilder text = new StringBuilder("📞 Oxirgi 2 oy ichida CANCELED qilgan buyurtmalar:\n\n");

        for (Map.Entry<String, String> entry : phoneOrders.entrySet()) {
            String name = entry.getValue(); // Mijoz ismi
            String phone = entry.getKey(); // Telefon raqami
            String status = blockedUsersRepository.existsByPhoneNumber(phone) ? "(Bloklangan)" : "(Aktiv)"; // Bloklangan yoki yo'q
            int cancelCount = orderHistoryRepository.countByClientPhoneNumberAndStatus(phone, OrderStatus.CANCELED); // Bekor qilganlar soni

            // Formatlangan matnni qo‘shish
            text.append(String.format("👤 %-5s %-5s - 📞 %-5s - ❌ %d bora\n",
                    name + status, "", phone, cancelCount));
        }
//        rows.add(List.of(createButton("Foydalanuvchi tarixi:", "PHONE_")));
        rows.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED_TO_WELCOME")));
        rows.add(List.of(createButton("Foydalanuvchilarni Bloklash", "BLOCK_USERS")));
        rows.add(List.of(createButton("Foydalanuvchilarni Blokdan chiqarish", "BLOCKED_USERS")));
        markup.setKeyboard(rows);
        sendMessageWithMarkup(chatId, String.valueOf(text), markup);
    }

    private void sendClientPhoneNumbers(long chatId) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<Client> recentClients = clientRepository.findClientsWithOrdersLast3Months(threeMonthsAgo);

        if (recentClients.isEmpty()) {
            sendMessage(chatId, "📌 Oxirgi 3 oyda buyurtma bergan mijozlar yo‘q.");
            return;
        }

        Map<String, Long> cancelCountMap = new HashMap<>();
        List<Object[]> canceledData = orderHistoryRepository.findCanceledOrdersCountForClients(threeMonthsAgo);

        for (Object[] obj : canceledData) {
            String phoneNumber = (String) obj[0];
            Long cancelCount = (Long) obj[1];
            cancelCountMap.put(phoneNumber, cancelCount);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Client client : recentClients) {
            String phoneNumber = client.getPhoneNumber();
            String cancelText = cancelCountMap.containsKey(phoneNumber) ? " ❌" + cancelCountMap.get(phoneNumber) + " marta" : "";
            String buttonText = client.getName() + " - " + phoneNumber + cancelText;
            String callbackData = "PHONE_" + phoneNumber;

            rows.add(List.of(createButton(buttonText, callbackData)));
        }

        rows.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_MENU")));
        markup.setKeyboard(rows);

        sendMessageWithMarkup(chatId, "📞 Oxirgi 3 oyda buyurtma bergan mijozlar:", markup);
    }


    private void sendOrderReport(long chatId, OrderStatus status) {
        List<OrderHistory> orders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                status, LocalDate.now().minusWeeks(2).atStartOfDay(), LocalDate.now().atTime(23, 59, 59));
        if (orders.isEmpty()) {
            sendMessage(chatId, "ORDERED bo'lgan buyurtmalar yo‘q. Barcha buyurtma SOLD yoki CANCELED deb belgilangan!");
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

        LocalDate botStartDate = orderHistoryRepository.findEarliestDate();
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

        LocalDate botStartDate = orderHistoryRepository.findEarliestDate();
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

        LocalDate botStartDate = orderHistoryRepository.findEarliestDate();
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

    private void sendCanceledUsersList(long chatId) {
        LocalDateTime twoMonthsAgo = LocalDateTime.now().minusMonths(2);
        List<OrderHistory> canceledOrders = orderHistoryRepository.findByStatusAndOrderDateBetween(
                OrderStatus.CANCELED, twoMonthsAgo, LocalDateTime.now());

        if (canceledOrders.isEmpty()) {
            sendMessage(chatId, "📌 Oxirgi 2 oyda buyurtmalarni bekor qilgan foydalanuvchilar yo‘q.");
            return;
        }

        Map<Client, Integer> userCancelCount = new HashMap<>();
        for (OrderHistory order : canceledOrders) {
            Client client = order.getClient();
            userCancelCount.put(client, userCancelCount.getOrDefault(client, 0) + 1);
        }

        StringBuilder text = new StringBuilder("📌 Oxirgi 2 oyda buyurtmalarni bekor qilgan foydalanuvchilar:\n\n");
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        int index = 1;

        for (Map.Entry<Client, Integer> entry : userCancelCount.entrySet()) {
            Client client = entry.getKey();
            int count = entry.getValue();
            String phone = client.getPhoneNumber();
            String name = client.getName();

            text.append(index).append(". ").append(name).append(" -> ").append(phone)
                    .append(" (").append(count).append(" ta buyurtmani bekor qilgan)\n");

            buttons.add(List.of(createButton("🚫 " + name + " -> " + phone, "BLOCK_USER_" + phone)));
            index++;
        }

        buttons.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_ADMIN_MENU")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);

        sendMessageWithMarkup(chatId, text.toString(), markup);
    }


//    private void handleBlockUser(long chatId, String phoneNumber, int days) {
//        Optional<Client> optionalClient = clientRepository.findByPhoneNumber(phoneNumber);
//        if (optionalClient.isEmpty()) {
//            sendMessage(chatId, "❌ Foydalanuvchi topilmadi.");
//            return;
//        }
//
//        Client client = optionalClient.get();
//        if (client.isBlocked()) {
//            sendMessage(chatId, "🚫 " + client.getName()+"-> "+ client.getPhoneNumber() + " allaqachon bloklangan. " +
//                    "Blok tugash vaqti: " + client.getBlockedUntil() + "      /start");
//            return;
//        }
//
//        client.blockForDays(days);
//        clientRepository.save(client);
//
//        sendMessage(chatId, "🚫 " + client.getName() +"-> "+ client.getPhoneNumber() + " " + days + " kun bloklandi.\n" +
//                "Blok tugash vaqti: " + client.getBlockedUntil() + "      /start");
//    }

    private void handleBlockUser(long chatId, String phoneNumber, int days) {
        Optional<Client> optionalClient = clientRepository.findByPhoneNumber(phoneNumber);
        if (optionalClient.isEmpty()) {
            sendMessage(chatId, "❌ Foydalanuvchi topilmadi.");
            return;
        }

        Client client = optionalClient.get();
        LocalDateTime blockUntil = LocalDateTime.now().plusDays(days);

        // **BlockedUser jadvaliga saqlash**
        BlockedUser blockedUser = blockedUserRepository.findByPhoneNumber(phoneNumber)
                .orElse(new BlockedUser());

        blockedUser.setPhoneNumber(phoneNumber);
        blockedUser.setBlockedUntil(blockUntil);
        blockedUserRepository.save(blockedUser); // ✅ Saqlaymiz

        // **Client jadvalida bloklanganlikni saqlash**
        client.setBlocked(true);  // ✅ `isBlocked=true`
        clientRepository.save(client);

        sendMessage(chatId, "🚫 " + client.getName() + " " + days + " kunga bloklandi.\n"
                + "📅 Tugash sanasi: " + blockUntil.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
    }


    private void handleUnblockUser(long chatId, String phoneNumber) {
        Optional<Client> optionalClient = clientRepository.findByPhoneNumber(phoneNumber);
        if (optionalClient.isEmpty()) {
            sendMessage(chatId, "❌ Foydalanuvchi topilmadi.");
            return;
        }

        Client client = optionalClient.get();

        // **BlockedUser jadvalidan o‘chirish**
        blockedUserRepository.deleteByPhoneNumber(phoneNumber); // ✅ O‘chiramiz

        // **Client jadvalida bloklanganligini olib tashlash**
        client.setBlocked(false);  // ✅ `isBlocked=false`
        clientRepository.save(client);

        sendMessage(chatId, "✅ " + client.getName() + " blokdan chiqarildi.");
    }




    private void confirmBlockUser(long chatId, String phoneNumber, int days) {
        LocalDateTime blockedUntil = LocalDateTime.now().plusDays(days);
        BlockedUser blockedUser = new BlockedUser(phoneNumber, blockedUntil);
        blockedUserRepository.save(blockedUser);

        sendMessage(chatId, "✅ " + phoneNumber + " foydalanuvchi " + days + " kunga bloklandi.");

        // **Foydalanuvchiga bloklanish haqida xabar yuborish**
        sendMessageToClientBot(phoneNumber, "🚫 Siz " + days + " kunga bloklandingiz. " +
                "Sabab: Buyurtmangizni bekor qildingiz. " +
                "Blok muddati tugagach, yana buyurtma bera olasiz.");

        sendCanceledUsersList(chatId);
    }
    private void sendBlockedUsers(long chatId) {
        List<Object[]> clients = orderHistoryRepository.findClientsWithCanceledOrdersLast2Months(LocalDateTime.now().minusMonths(2));

        if (clients.isEmpty()) {
            sendMessage(chatId, "❌ Bloklanadigan foydalanuvchilar yo‘q.");
            return;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Object[] obj : clients) {
            Client client = (Client) obj[0];  // Mijoz obyekti
            Long cancelCount = (Long) obj[1]; // Bekor qilingan buyurtmalar soni

            if (!client.isBlocked()) {
                String buttonText = "🚫 " + client.getName() + " -> " + client.getPhoneNumber() +
                        " (2 oy ichida " + cancelCount + " ta bekor qilingan)";
                String callbackData = "BLOCK_USER_" + client.getPhoneNumber();
                rows.add(List.of(createButton(buttonText, callbackData)));
            }
        }

        rows.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED")));
        markup.setKeyboard(rows);

        String text = "📋 Bloklash uchun foydalanuvchini tanlang:";
        sendMessageWithMarkup(chatId, text, markup);
    }


    private void unblockUser(long chatId, String phoneNumber) {
        Optional<Client> optionalClient = clientRepository.findByPhoneNumber(phoneNumber);
        if (optionalClient.isEmpty()) {
            sendMessage(chatId, "❌ Foydalanuvchi topilmadi.");
            return;
        }

        Client client = optionalClient.get();
        if (!client.isBlocked()) {
            sendMessage(chatId, "✅ " + client.getName() + " allaqachon blokdan chiqarilgan.");
            return;
        }

        client.unblock();
        clientRepository.save(client);

        sendMessage(chatId, "✅ " + client.getName() + " blokdan chiqarildi.");
    }


    private void sendMessageToClientBot(String phoneNumber, String text) {
        // Foydalanuvchini telefon raqami bo‘yicha topamiz
        Client client = clientRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (client != null) {
            long chatId = client.getChatId();
            sendMessage(chatId, text);
        }
    }
    private void sendUnblockedUsers(long chatId) {
        List<Client> blockedClients = clientRepository.findByIsBlockedTrue(); // 🔹 Faqat bloklanganlarni olish

        if (blockedClients.isEmpty()) {
            sendMessage(chatId, "❌ Bloklangan foydalanuvchilar yo‘q.");
            return;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Client client : blockedClients) {
            rows.add(List.of(createButton("✅ " + client.getName(), "UNBLOCK_USER_" + client.getPhoneNumber())));
        }

        rows.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_MENU")));
        markup.setKeyboard(rows);

        sendMessageWithMarkup(chatId, "🔓 Blokdan chiqarish uchun foydalanuvchini tanlang:", markup);
    }

//    private void sendUnblockedUsers(long chatId) {
//        List<Client> clients = clientRepository.findAll();
//        if (clients.isEmpty()) {
//            sendMessage(chatId, "❌ Bloklangan foydalanuvchilar yo‘q.");
//            return;
//        }
//
//        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
//        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
//
//        for (Client client : clients) {
//            if (client.isBlocked()) { // Faqat bloklanganlarni chiqarish
//                rows.add(List.of(createButton("✅ " + client.getName(), "UNBLOCK_USER_" + client.getPhoneNumber())));
//            }
//        }
//
//        rows.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED")));
//        markup.setKeyboard(rows);
//
//        sendMessageWithMarkup(chatId, "🔓 Blokdan chiqarish uchun foydalanuvchini tanlang:", markup);
//    }


    private void sendBlockedUsersList(long chatId) {
        List<BlockedUser> blockedUsers = blockedUserRepository.findAll();
        if (blockedUsers.isEmpty()) {
            sendMessage(chatId, "🚫 Hozircha hech kim bloklanmagan.");
            return;
        }

        StringBuilder text = new StringBuilder("🚫 Bloklangan foydalanuvchilar:\n\n");
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        int index = 1;

        for (BlockedUser blockedUser : blockedUsers) {
            long remainingHours = ChronoUnit.HOURS.between(LocalDateTime.now(), blockedUser.getBlockedUntil());
            long days = remainingHours / 24;
            long hours = remainingHours % 24;

            text.append(index).append(". ").append(blockedUser.getPhoneNumber())
                    .append(" - ").append(days).append(" kun ").append(hours).append(" soat qoldi.\n");

            buttons.add(List.of(createButton("🚪 Blokdan chiqarish", "UNBLOCK_USER_" + blockedUser.getPhoneNumber())));
            index++;
        }

        buttons.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_ADMIN_MENU")));
        sendMessageWithMarkup(chatId, text.toString(), new InlineKeyboardMarkup(buttons));
    }

    private void showBlockOptions(long chatId, String phoneNumber) {
        String text = "📌 Siz +" + phoneNumber + " foydalanuvchini bloklamoqchisiz.\n" +
                "Qancha vaqtga bloklamoqchisiz?";

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            // Bu tugmalar formatini "BLOCK_USER_{phone}_{days}" qilib yarating.
            buttons.add(List.of(createButton(i + " kun", "BLOCK_USER_" + phoneNumber + "_" + i)));
        }
        buttons.add(List.of(createButton("⬅️ Orqaga", "BACK_TO_CANCELED_LIST")));

        sendMessageWithMarkup(chatId, text, new InlineKeyboardMarkup(buttons));
    }

    private void sendUserOrderHistory(long chatId, String phoneNumber, Client client) {
        LocalDateTime twoMonthsAgo = LocalDateTime.now().minusMonths(2); // 2 hafta -> 2 oy

        // Buyurtmalarni status bo‘yicha olish
        List<OrderHistory> userOrders = orderHistoryRepository.findByClientAndOrderDateAfter(client, twoMonthsAgo);

        if (userOrders.isEmpty()) {
            sendMessage(chatId, "❌ " + phoneNumber + " oxirgi 2 oyda hech qanday buyurtma qilmagan.");
            return;
        }

        StringBuilder report = new StringBuilder("📜 *" + phoneNumber + "* oxirgi 2 oylik buyurtmalari:\n\n");

        for (OrderHistory order : userOrders) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            String formattedDate = order.getOrderDate().format(formatter);

            // Buyurtma statusiga qarab teg qo‘shish
            String statusTag = switch (order.getStatus()) {
                case SOLD -> "🟢 Sotilgan Buyurtma";
                case CANCELED -> "🔴 Bekor qilingan Buyurtma";
                case ORDERED -> "📦 ORDERED qilingan Buyurtma";
            };

            report.append(statusTag).append("\n")
                    .append("🆔 Buyurtma: ").append(order.getOrderIndex()).append("\n")
                    .append("👤 *Ism:* ").append(client.getName()).append("\n")
                    .append("📞 *Telefon:* ").append(phoneNumber).append("\n")
                    .append("⏳ Olib ketish vaqti: ").append(order.getClient().getPickupTime()).append("\n")
                    .append("📅 Buyurtma berilgan vaqt: ").append(formattedDate).append("\n")
                    .append("💰 *Jami narx:* ").append(order.getTotalPrice()).append(" so'm\n")
                    .append("------------------\n");
        }

        // Inline tugmalar
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        buttons.add(List.of(createButton("🚫 Foydalanuvchini Bloklash", "BLOCK_USER_" + phoneNumber)));
        buttons.add(List.of(createButton("⬅️ Orqaga", "SHOW_CLIENT_PHONES"))) ;

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(buttons);
        sendMessageWithMarkup(chatId, report.toString(), markup);
    }
}
