package com.example.demo.service;

import com.example.demo.config.ClientBotConfig;
import com.example.demo.model.*;
import com.example.demo.repository.ClientRepository;
import com.example.demo.repository.MenuRepository;
import com.example.demo.repository.OrderHistoryRepository;
import com.example.demo.repository.OrdersRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ClientBot extends TelegramLongPollingBot {

    private final ClientBotConfig config;
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;


    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    private static final long ADMIN_BOT_CHAT_ID = 1291967688L;
    private static final long HISOBOT_Group_CHAT_ID = -1002265983705L;

    private static final long groupId = -1002332417212L;


    private final Map<Long, Map<Long, Integer>> orders = new HashMap<>();
    private final Map<Long, Long> currentOrder = new HashMap<>();
    private final Map<Long, Map<Long, Integer>> basket = new HashMap<>();
    private long userCounter = 1;
    private Map<Long, Long> userIdMap = new HashMap<>();

    private final Map<Long, Boolean> awaitingAddress = new HashMap<>();
    private final Map<Long, Boolean> awaitingPickupTime = new HashMap<>();

    @Autowired
    public ClientBot(ClientBotConfig config) {
        this.config = config;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "Botni boshlash"));
        listofCommands.add(new BotCommand("/yordam", "Yordam olish"));

        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    public void broadcastMessageToClients(String messageText) {
        List<Client> clients = clientRepository.findAll();

        for (Client client : clients) {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(client.getChatId()));
                message.setText(messageText);
                execute(message);
                System.out.println("Xabar yuborildi: " + client.getChatId());
            } catch (TelegramApiException e) {
                System.err.println("Xabar yuborishda xatolik yuz berdi: " + client.getChatId());
                e.printStackTrace();
            }
        }
    }

    private void sendMessageWithPhoneRequest(long chatId, String text) {
        // Tugma yaratish
        KeyboardButton contactButton = new KeyboardButton();
        contactButton.setText("📞 Telefon raqamni yuborish");
        contactButton.setRequestContact(true); // Foydalanuvchi kontaktini yuboradi

        // Tugma panelini yaratish
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true); // Tugma faqat bir marta ko‘rsatiladi

        // Tugmalar ro‘yxatini o‘rnatish
        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);
        replyKeyboardMarkup.setKeyboard(Collections.singletonList(row));

        // Xabarni tugma bilan yuborish
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onUpdateReceived(Update update) {
        long chatId;
        Client client;


        if (update.hasMessage() && update.getMessage().hasContact()) {
            chatId = update.getMessage().getChatId();
            client = clientRepository.findById(chatId).orElse(null);
            Contact contact = update.getMessage().getContact();
            String phoneNumber = contact.getPhoneNumber(); // Telefon raqami
            client.setPhoneNumber(phoneNumber); // Bazaga saqlash
            clientRepository.save(client);

            sendMessages(chatId, "Telefon raqamingiz saqlandi ✅");
            sendMainMenu(chatId); // Asosiy menyuni yuborish
            return;
        }

         // // Group Id sini olish
        if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            System.out.println("Guruh ID: " + chatId);
        }


        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            chatId = update.getMessage().getChatId();

            // Foydalanuvchini bazadan olish yoki yangi yaratish
            client = clientRepository.findById(chatId).orElse(null);

            // === 1. Agar foydalanuvchi /start yuborsa ===
            if (message.equals("/start")) {
                if (client == null) {
                    client = new Client(chatId); // Yangi obyekt yaratamiz
                    clientRepository.save(client);
                    sendMessages(chatId, "Assalomu alaykum! Botimizga xush kelibsiz! Iltimos, ismingizni kiriting:");
                    return;
                } else if (client.getName() == null || Objects.equals(client.getName(), "Menu") || Objects.equals(client.getName(), "Savatcha") || Objects.equals(client.getName(), "Buyurtma berish")) {
                    sendMessages(chatId, "Iltimos, ismingizni kiriting:");
                    return;
                } else if (client.getPhoneNumber() == null) {
                    sendMessageWithPhoneRequest(chatId, "Iltimos, telefon raqamingizni yuboring:");
                    return;
                }
                sendMessages(chatId, "Assalomu alaykum, " + client.getName() + "! Botimizga xush kelibsiz!");
                sendMainMenu(chatId);
                return;
            }


            // === 2. Agar foydalanuvchi ismini kiritayotgan bo‘lsa ===
            if (client != null && client.getName() == null) {
                client.setName(message);
                clientRepository.save(client);
                sendMessageWithPhoneRequest(chatId, "Rahmat, " + message + "! Endi telefon raqamingizni yuboring:");
                return;
            }

            // === 3. Agar foydalanuvchi telefon raqamini kiritayotgan bo‘lsa ===
            if (client != null && client.getPhoneNumber() == null) {
                client.setPhoneNumber(message);
                clientRepository.save(client);
                sendMessages(chatId, "Siz muvaffaqiyatli ro‘yxatdan o‘tdingiz ✅");
                sendMainMenu(chatId);
                return;
            }

            if (awaitingAddress.getOrDefault(chatId, false)) {
                client.setDeliveryAddress(message);
                client.setPickupTime(null); // Eski vaqtni tozalash
                clientRepository.save(client);
                awaitingAddress.put(chatId, false);
                confirmOrder(chatId);
                return;
            }

            // === 4. Agar olib ketish vaqtini kiritayotgan bo'lsa ===
            if (awaitingPickupTime.getOrDefault(chatId, false)) {
                client.setPickupTime(message);
                client.setDeliveryAddress(null); // Eski manzilni tozalash
                clientRepository.save(client);
                awaitingPickupTime.put(chatId, false);
                confirmOrder(chatId);
                return;
            }

            // === 4. Asosiy menyu komandalarini tekshirish ===
            switch (message) {
                case "yordam":
                    sendMessages(chatId, "shulardan foydalaning\n" + "/Menular_bilan_tanishish - yangi menu qo'shish\n" + "/Savatcha - menular ro'yhatini ko'rish\n" + "/Menudan_olish - menularni raqami bo'yicha o'chirish\n" + "/other - admindan yordam");
                    break;
                case "Menu":
                case "/Menular_bilan_tanishish":
                case "Menular bilan tanishish":
                    showMenus(chatId);
                    break;
                case "Savatcha":
                    showBasket(chatId);
                    break;
                case "Buyurtma berish":
                    showMenu(chatId);
                    break;
                case "/other":
                    sendMessage(chatId, "@Rakhimov_Isroil bilan bog'lanishingiz mumkin!");
                    break;
                default:
                    sendMessage(chatId, "Iltimos, menyudagi tugmalardan foydalaning!");
            }
        }

        // Agar callback bosilgan bo'lsa
        else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void sendInlineKeyboard(long chatId, String text, String[] buttons, String[] callbacks) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < buttons.length; i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(buttons[i]);
            button.setCallbackData(callbacks[i]);
            rows.add(Collections.singletonList(button));
        }

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

    private void sendMessages(long chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(message);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(long chatId, String text) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableHtml(true);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true); // Tugmalarni kichraytirish
        keyboardMarkup.setOneTimeKeyboard(false); // Klaviatura ekranda qoladi
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();

        row1.add("Menu");
        row1.add("Savatcha");
        row1.add("Buyurtma berish");
        keyboardRows.add(row1);

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Asosiy menyu:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("Menuni ko'rish", "SHOW_MENU"));
        row1.add(createButton("Savatcha", "VIEW_BASKET"));

        rows.add(row1);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showMenus(long chatId) {

        List<Menu> activeMenus = menuRepository.findByIsActive("Sotilmoqda");
        if (activeMenus.isEmpty()) {
            sendMessage(chatId, "Hozirda sotuvda mahsulotlar mavjud emas. Bot egasi @Raximov_Isroil bilan bog'laning!");
            return;
        }

        StringBuilder menuText = new StringBuilder("Ovqatlar ro'yxati:\n\n");

        for (Menu menu : activeMenus) {
            menuText.append(menu.getId()).append(". ").append(menu.getFood_name()).append(" - ").append(menu.getFood_price()).append(" so'm\n");
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("Buyurtma berish", "SHOW_ORDERS_MENU"));
        row1.add(createButton("Savatcha", "VIEW_BASKET"));

        SendMessage message = new SendMessage(String.valueOf(chatId), menuText.toString());

        rows.add(row1);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showMenu(long chatId) {
        List<Menu> activeMenus = menuRepository.findByIsActive("Sotilmoqda");
        if (activeMenus.isEmpty()) {
            sendMessage(chatId, "Hozirda sotuvda mahsulotlar mavjud emas.");
            return;
        }

        StringBuilder menuText = new StringBuilder("Ovqatlar ro'yxati: \n\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Menu menu : activeMenus) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(menu.getId() + ". " + menu.getFood_name() + "  -  " + menu.getFood_price() + " so'm", "FOOD_" + menu.getId()));
            rows.add(row);
        }

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createButton("Orqaga", "SHOW_MENU"));
        rows.add(backRow);

        markup.setKeyboard(rows);
        SendMessage message = new SendMessage(String.valueOf(chatId), menuText.toString());
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void clearBasket(long chatId) {
        orders.remove(chatId);

        sendMessage(chatId, "🗑️ Savatchangiz tozalandi.");
    }

    private long getOrCreateUserId(long chatId) {
        if (!userIdMap.containsKey(chatId)) {
            userIdMap.put(chatId, userCounter++);
        }
        return userIdMap.get(chatId);
    }

    private void confirmOrder(long chatId) {
        Map<Long, Integer> userOrders = orders.get(chatId);
        if (userOrders == null || userOrders.isEmpty()) {
            sendMessage(chatId, "❗ Savatchangiz bo'sh.");
            return;
        }

        Client client = clientRepository.findById(chatId).orElse(null);
        if (client == null || client.getName() == null || client.getPhoneNumber() == null) {
            sendMessage(chatId, "❗ Iltimos, oldin ro‘yxatdan o‘ting! \n/start tugmasini bosib ismingiz va telefon raqamingizni kiriting.");
            return;
        }

        long userId = getOrCreateUserId(chatId);
        StringBuilder confirmedOrderDetails = new StringBuilder();
        confirmedOrderDetails.append("🆔: ").append(userId).append("\n");

        double totalAmount = 0;
        String userName = client.getName();
        String userPhone = client.getPhoneNumber();

        confirmedOrderDetails.append("👤 *Ism:* ").append(userName).append("\n");
        confirmedOrderDetails.append("📞 *Telefon:* ").append(userPhone).append("\n");

        if (client.getDeliveryAddress() != null) {
            confirmedOrderDetails.append("📍 Manzil: ").append(client.getDeliveryAddress()).append("\n\n");
        } else {
            confirmedOrderDetails.append("⏳ Olib ketish vaqti: ").append(client.getPickupTime()).append("\n\n");
        }

        confirmedOrderDetails.append("🍽 *Buyurtma tafsilotlari:* \n");
        for (Map.Entry<Long, Integer> entry : userOrders.entrySet()) {
            long foodId = entry.getKey();
            int quantity = entry.getValue();
            Menu menu = menuRepository.findById(foodId).orElse(null);

            if (menu != null && quantity > 0) {
                double itemTotal = menu.getFood_price() * quantity;
                totalAmount += itemTotal;
                confirmedOrderDetails.append("🍔 ").append(menu.getFood_name())
                        .append(" x ").append(quantity)
                        .append(" = ").append(itemTotal).append(" so'm\n");
            }
        }

        confirmedOrderDetails.append(String.format("\n💰 Jami narx: %.2f so'm\n", totalAmount));

        // ✅ Buyurtmani ORDERED statusida bazaga saqlaymiz
        Orders order = new Orders(client, userName, userPhone, confirmedOrderDetails.toString(), totalAmount, LocalDateTime.now(), OrderStatus.ORDERED);
        Orders savedOrder = ordersRepository.save(order);

        System.out.println("✅ Buyurtma saqlandi! ID: " + savedOrder.getId());

        // ✅ Buyurtma tarixini ORDERED statusida saqlaymiz
        OrderHistory history = new OrderHistory(client, savedOrder.getId(), confirmedOrderDetails.toString(), OrderStatus.ORDERED);
        orderHistoryRepository.save(history);

        // ✅ Mijozga habar yuboramiz
        sendMessage(chatId, "✅ Buyurtmangiz oshxonachilarga yetkazildi!\n\n" + confirmedOrderDetails.toString());

        // ✅ Gruppaga habar yuboramiz
        sendOrderToGroup(confirmedOrderDetails.toString(), chatId);

        // ✅ Buyurtmalarni tozalash
        orders.remove(chatId);

        sendMessage(chatId, "Yana buyurtma berishni hohlasangiz tugmalardan foydalaning");
    }




    private void sendOrderToGroup(String orderDetails, Long chatId) {
        String groupIdd = "-1002332417212";

        Long orderId = extractOrderId(orderDetails);
        if (orderId == null) {
            sendMessage(chatId, "❌ Xatolik: Buyurtma ID aniqlanmadi.");
            return;
        }

        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null) {
            sendMessage(chatId, "❌ Xatolik: Buyurtma topilmadi.");
            return;
        }

        Client client = clientRepository.findByChatId(chatId).orElse(null);
        String userName = (client != null) ? client.getName() : "Noma'lum";
        String userPhone = (client != null) ? client.getPhoneNumber() : "Noma'lum";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("✅ Sotildi", "MARK_SOLD_" + orderId));
        row.add(createButton("❌ Bekor qilindi", "MARK_CANCELED_" + orderId + "_" + userName + "_" + userPhone));
        rows.add(row);
        markup.setKeyboard(rows);

        sendMessageWithMarkup(groupIdd, "📦 **Yangi buyurtma!**\n\n" + orderDetails, markup);
    }


    private Long extractOrderId(String orderDetails) {
        Pattern pattern = Pattern.compile("🆔: (\\d+)");
        Matcher matcher = pattern.matcher(orderDetails);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }


    private void showOrderDetails(long chatId, long foodId, int messageId, int quantity) {

        Menu menu = menuRepository.findById(foodId).orElse(null);
        if (menu == null) {
            sendMessage(chatId, "Ovqat topilmadi.");
            return;
        }

        String details = "Tanlangan mahsulot:\n" + "ID: " + menu.getId() + "\n" + "Nomi: " + menu.getFood_name() + "\n" + "Narxi: " + (menu.getFood_price() * quantity) + " so'm\n" + "Miqdor: " + quantity;

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("+", "ADD_QUANTITY_" + foodId));
        row1.add(createButton("-", "REMOVE_QUANTITY_" + foodId));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("Savatga qaytish", "VIEW_BASKET" + foodId));
        row2.add(createButton("Orqaga", "SHOW_ORDERS_MENU"));

        rows.add(row1);
        rows.add(row2);

        markup.setKeyboard(rows);

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(details);
        editMessage.setReplyMarkup(markup);

        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void updateOrder(long chatId, long foodId, int messageId, int delta) {

        Map<Long, Integer> userOrders = orders.getOrDefault(chatId, new HashMap<>());
        int currentQuantity = userOrders.getOrDefault(foodId, 0);
        int newQuantity = Math.max(currentQuantity + delta, 0);

        userOrders.put(foodId, newQuantity);
        orders.put(chatId, userOrders);

        showOrderDetails(chatId, foodId, messageId, newQuantity);
    }

    private void finalizeOrder(long chatId) {
        long foodId = currentOrder.get(chatId);
        Map<Long, Integer> userBasket = basket.getOrDefault(chatId, new HashMap<>());

        int quantity = userBasket.getOrDefault(foodId, 0) + 1;
        userBasket.put(foodId, quantity);

        // Savatchani yangilash
        basket.put(chatId, userBasket);

        sendMessage(chatId, "Buyurtma savatchaga qo'shildi!");
        currentOrder.remove(chatId);
    }

    private void showBasket(long chatId) {

        Map<Long, Integer> userOrders = orders.get(chatId);
        if (userOrders == null || userOrders.isEmpty()) {
            sendMessage(chatId, "Savatchangiz bo'sh.");
            return;
        }

        StringBuilder basketDetails = new StringBuilder("Savatchadagi buyurtmalar:\n");
        int totalAmount = 0;

        for (Map.Entry<Long, Integer> entry : userOrders.entrySet()) {
            long foodId = entry.getKey();
            int quantity = entry.getValue();
            Menu menu = menuRepository.findById(foodId).orElse(null);

            if (menu != null) {
                double itemTotal = menu.getFood_price() * quantity;
                totalAmount += itemTotal;

                basketDetails.append("ID: ").append(menu.getId()).append("\nNomi: ").append(menu.getFood_name()).append("\nNarxi: ").append(menu.getFood_price()).append(" so'm").append("\nMiqdor: ").append(quantity).append("\nJami: ").append(itemTotal).append(" so'm\n\n");
            }
        }

        basketDetails.append("Umumiy summa: ").append(totalAmount).append(" so'm");

        SendMessage message = new SendMessage(String.valueOf(chatId), basketDetails.toString());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("Savatchani tozalash", "CLEAR_BASKET"));
        row1.add(createButton("Tasdiqlash", "CONFIRM_ORDER"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("Savatchani yangilash", "UPDATE_BASKET"));
        row2.add(createButton("Orqaga", "SHOW_ORDERS_MENU"));

        rows.add(row1);
        rows.add(row2);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

    private void updateBasket(long chatId) {
        Map<Long, Integer> userOrders = orders.get(chatId);

        if (userOrders == null || userOrders.isEmpty()) {
            sendMessage(chatId, "🛒 Savatchangiz bo'sh.");
            return;
        }

        StringBuilder basketDetails = new StringBuilder("🛒 Savatchangiz:\n\n");
        int totalAmount = 0;

        for (Map.Entry<Long, Integer> entry : userOrders.entrySet()) {
            long foodId = entry.getKey();
            int quantity = entry.getValue();
            Menu menu = menuRepository.findById(foodId).orElse(null);

            if (menu != null) {
                double itemTotal = menu.getFood_price() * quantity;
                totalAmount += itemTotal;

                basketDetails.append("🍔 ").append(menu.getFood_name()).append("\nMiqdor: ").append(quantity).append("\nJami: ").append(itemTotal).append(" so'm\n\n");
            }
        }
        basketDetails.append("💰 Umumiy summa: ").append(totalAmount).append(" so'm");

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(basketDetails.toString());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : userOrders.entrySet()) {
            long foodId = entry.getKey();
            Menu menu = menuRepository.findById(foodId).orElse(null);

            if (menu != null) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(createButton("➕ " + menu.getFood_name(), "ADD_QUANTITY_" + foodId));
                row.add(createButton("➖ " + menu.getFood_name(), "REMOVE_QUANTITY_" + foodId));
                rows.add(row);
            }
        }

        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        confirmRow.add(createButton("Orqaga", "SHOW_MENU"));
        confirmRow.add(createButton("Tasdiqlash", "CONFIRM_ORDER"));

        rows.add(confirmRow);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void handleCancelOrder(String callbackData, long chatId) {
        String[] data = callbackData.split("_");
        if (data.length < 3) {
            sendMessage(chatId, "❌ Xatolik: Callback ma'lumotlari noto‘g‘ri.");
            return;
        }


        long orderId;
        try {
            orderId = Long.parseLong(data[2]);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Xatolik: Noto‘g‘ri buyurtma ID.");
            return;
        }

        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null) {
            sendMessage(chatId, "❌ Xatolik: Buyurtma topilmadi.");
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELED) {
            sendMessage(chatId, "⚠️ Bu buyurtma avval bekor qilingan!");
            return;
        }

        // ✅ Agar buyurtma allaqachon SOLD bo‘lsa, bekor qilib bo‘lmaydi!
        if (order.getStatus() == OrderStatus.SOLD) {
            sendMessage(chatId, "⚠️ Bu buyurtma allaqachon sotilgan, uni bekor qilib bo‘lmaydi!");
            return;
        }

        // ✅ Takrorlanishni oldini olish uchun mahsulot ro‘yxatini ajratamiz
        String orderItems = extractOrderItems(order.getOrderDetails());

        // ✅ Buyurtmani CANCELED deb belgilaymiz
        order.setStatus(OrderStatus.CANCELED);
        ordersRepository.save(order);

        // ✅ Buyurtma tarixini yangilaymiz
        OrderHistory orderHistory = orderHistoryRepository.findByOrderId(orderId);
        if (orderHistory != null) {
            orderHistory.updateStatus(OrderStatus.CANCELED);
            orderHistoryRepository.save(orderHistory);
        } else {
            OrderHistory newHistory = new OrderHistory(order.getClient(), order.getId(), order.getOrderDetails(), OrderStatus.CANCELED);
            orderHistoryRepository.save(newHistory);
        }

        // ✅ Admin va oshxona uchun xabar
        String cancelMessage = "❌ Buyurtma bekor qilindi!\n\n" +
                "🆔 Buyurtma ID: " + orderId + "\n" +
                "👤 Foydalanuvchi: " + order.getUserName() + "\n" +
                "📞 Telefon: " + order.getUserPhone() + "\n\n" +
                "🍽 *Buyurtma tafsilotlari:* \n" + orderItems + "\n\n" +
                "💰 Jami narx: " + order.getTotalPrice() + " so'm\n\n" +
                "🔴 **Admin bilan bog‘laning!**";

        sendMessage(groupId, cancelMessage);
    }

    // ✅ Faqat buyurtmadagi mahsulotlarni ajratib olish uchun yordamchi metod
    private String extractOrderItems(String orderDetails) {
        StringBuilder items = new StringBuilder();
        String[] lines = orderDetails.split("\n");
        for (String line : lines) {
            if (line.startsWith("🍔") || line.startsWith("🥗") || line.startsWith("🍕")) { // Faqat mahsulot qatorlarini olish
                items.append(line).append("\n");
            }
        }
        return items.toString().trim();
    }


    private void handleSoldOrder(String callbackData, long chatId) {
        long orderId = Long.parseLong(callbackData.split("_")[2]);
        Orders order = ordersRepository.findById(orderId).orElse(null);

        if (order == null) {
            sendMessage(chatId, "❌ Xatolik: Buyurtma topilmadi.");
            return;
        }

        // ✅ Faqat shu orderId uchun statusni tekshiramiz
        if (order.getStatus() == OrderStatus.SOLD) {
            sendMessage(chatId, "⚠️ Bu buyurtma allaqachon sotilgan!");
            return;
        }

        // ✅ Buyurtmani SOLD deb belgilaymiz
        order.setStatus(OrderStatus.SOLD);
        ordersRepository.save(order);

        // ✅ Buyurtma tarixini yangilaymiz
        OrderHistory orderHistory = orderHistoryRepository.findByOrderId(orderId);
        if (orderHistory != null) {
            orderHistory.updateStatus(OrderStatus.SOLD);
            orderHistoryRepository.save(orderHistory);
        } else {
            OrderHistory newHistory = new OrderHistory(order.getClient(), order.getId(), order.getOrderDetails(), OrderStatus.SOLD);
            orderHistoryRepository.save(newHistory);
        }

        sendMessage(groupId, "✅ Buyurtma sotildi! ID: " + orderId);
    }

//    private void handleSoldOrder(String callbackData, long chatId) {
//        long orderId = Long.parseLong(callbackData.split("_")[2]);
//        Orders order = ordersRepository.findById(orderId).orElse(null);
//
//        if (order == null) {
//            sendMessage(chatId, "❌ Buyurtma topilmadi.");
//            return;
//        }
//
//        chatId = order.getChatId(); // Mijozning chat ID sini olamiz
//
//        // ✅ Mijozning barcha "ORDERED" statusdagi buyurtmalarini olish
//        List<Orders> clientOrders = ordersRepository.findByChatIdAndStatus(chatId, OrderStatus.ORDERED);
//        if (clientOrders.isEmpty()) {
//            sendMessage(groupId, "❌ Mijozda tasdiqlanmagan buyurtmalar yo‘q.");
//            return;
//        }
//
//        // ✅ Sotilgan buyurtmalar ro‘yxatini yig‘ish
//        StringBuilder soldOrdersList = new StringBuilder("✅ Quyidagi buyurtmalar sotildi:\n\n");
//
//        for (Orders clientOrder : clientOrders) {
//            clientOrder.setStatus(OrderStatus.SOLD);
//            ordersRepository.save(clientOrder);
//
//            soldOrdersList.append("🆔 Buyurtmachi IDsi: ").append(clientOrder.getOrderDetails()).append("\n\n");
//
//            System.out.println("✅ Buyurtma SOLD statusiga o‘tdi: ID = " + clientOrder.getId());
//        }
//
//        sendMessage(groupId, soldOrdersList.toString()); // 🔹 Guruhga sotilgan buyurtmalarni chiqarish
//    }
//
//
//    private void handleCancelOrder(String callbackData, long chatId) {
//        System.out.println("🚨 Bekor qilish tugmasi bosildi: " + callbackData);
//
//        // 1️⃣ Callback ma’lumotlarini ajratib olish
//        String[] data = callbackData.split("_");
//        if (data.length < 3) {
//            sendMessage(chatId, "❌ Xatolik: Callback ma'lumotlari noto‘g‘ri.");
//            return;
//        }
//
//        // 2️⃣ Order ID olish
//        long orderId;
//        try {
//            orderId = Long.parseLong(data[2]);
//        } catch (NumberFormatException e) {
//            sendMessage(chatId, "❌ Xatolik: Noto‘g‘ri buyurtma ID.");
//            return;
//        }
//
//        // 3️⃣ Buyurtmani topish
//        Orders order = ordersRepository.findById(orderId).orElse(null);
//        if (order == null) {
//            System.out.println("❌ Xatolik: Buyurtma topilmadi! ID: " + orderId);
//            sendMessage(chatId, "❌ Xatolik: Buyurtma topilmadi.");
//            return;
//        }
//        // 4️⃣ Foydalanuvchi ma’lumotlarini olish
//        String userName = order.getUserName() != null ? order.getUserName() : "Noma'lum";
//        String userPhone = order.getUserPhone() != null ? order.getUserPhone() : "Noma'lum";
//        String orderDetails = order.getOrderDetails();
//
//
//        String cancelMassage ="✅ Buyurtma CANCELED qilindi!\n\n"+
//                "✅ Buyurtma topildi! ID: " + orderId + "\n" +
//                "👤 Foydalanuvchi ismi: " + userName + "\n" +
//                "📞 Telefon raqami: " + userPhone + "\n" +
//                "🍽 *Buyurtma tafsilotlari:* \n" + orderDetails + "\n\n" +
//                "🔴 **Admin bilan bog‘laning!**";
//
////        System.out.println(
////                "✅ Buyurtma topildi! ID: " + orderId +
////                "👤 Foydalanuvchi ismi: " + userName +
////                "📞 Telefon raqami: " + userPhone
////
////        );
//
//        System.out.println("👤 Foydalanuvchi ismi: " + userName);
//        System.out.println("📞 Telefon raqami: " + userPhone);
//
//        // 5️⃣ Buyurtmani "CANCELED" statusiga o‘tkazish
//        if (order.getStatus() != OrderStatus.CANCELED) {
//            order.setStatus(OrderStatus.CANCELED);
//            ordersRepository.save(order);
//            System.out.println("✅ Buyurtma CANCELED qilindi! ID: " + orderId);
//        } else {
//            System.out.println("⚠️ Buyurtma allaqachon bekor qilingan! ID: " + orderId);
//        }
//
//        // 6️⃣ Hisobot botiga xabar yuborish
////        String messageText = "⚠️ **Buyurtma bekor qilindi!**\n\n" +
////                "🆔 Buyurtma ID: " + orderId + "\n" +
////                "👤 **Ism:** " + userName + "\n" +
////                "📞 **Telefon:** " + userPhone + "\n" +
////                "🍽 *Buyurtma tafsilotlari:* \n" + orderDetails + "\n\n" +
////                "🔴 **Admin bilan bog‘laning!**";
//
//        sendMessage(groupId, cancelMassage);  // ✅ Hisobot botining guruhiga yuborish
//
//        // 7️⃣ Oshxona chatiga ham xabar yuborish
////        sendMessage(chatId, "✅ Buyurtma bekor qilindi va hisobot botiga yuborildi.");
//
//        // ✅ Console log
//        System.out.println("📩 Hisobot botiga xabar yuborildi!");
//    }

    private void sendMessageToHisobot(String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(HISOBOT_Group_CHAT_ID)); // ✅ Hisobot bot guruhining chat ID sini ishlatamiz
        message.setText(messageText);

        try {
            execute(message); // ✅ Xabar yuborish
            System.out.println("✅ Hisobot botga xabar yuborildi!");
        } catch (TelegramApiException e) {
            System.out.println("❌ Xatolik: Hisobot botga xabar yuborilmadi!");
            e.printStackTrace();
        }
    }


//    // ✅ Hisobot botiga xabar yuborish
//    private void sendMessageToBot(String botToken, String chatId, String messageText) {
//        // ✅ Tokenni parametr sifatida ishlatish
//        botToken = "7594485855:AAGcoc9PlWKLqMBtf7NTUJU6lFf6MJl8mfs";
//        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
//
//        RestTemplate restTemplate = new RestTemplate();
//        Map<String, String> requestBody = new HashMap<>();
//        requestBody.put("chat_id", chatId);
//        requestBody.put("text", messageText);
//        requestBody.put("parse_mode", "Markdown");
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
//
//        try {
//            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
//            System.out.println("✅ Xabar yuborildi! Javob: " + response.getBody());
//        } catch (Exception e) {
//            System.out.println("❌ Xatolik: Xabar yuborilmadi!");
//            e.printStackTrace();
//        }
//    }



    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        if (callbackData.startsWith("MARK_CANCELED_")) {
            handleCancelOrder(callbackData, chatId);
        } else if (callbackData.startsWith("MARK_SOLD_")) {
            handleSoldOrder(callbackData, chatId);
        }

//        if (callbackData.startsWith("MARK_CANCELED|")) {
//            String[] data = callbackData.split("\\|"); // "|" maxsus belgi, shuning uchun "\\|" yoziladi
//
//            if (data.length < 2) { // Agar faqat orderId kelsa
//                sendMessage(chatId, "❌ Xatolik: Callback ma'lumotlari noto‘g‘ri.");
//                return;
//            }
//
//            long orderId = Long.parseLong(data[1]);
//            Orders order = ordersRepository.findById(orderId).orElse(null);
//
//            if (order == null) {
//                sendMessage(chatId, "❌ Xatolik: Buyurtma topilmadi.");
//                return;
//            }
//
//            // ✅ Buyurtmani "CANCELED" statusiga o‘tkazish
//            order.setStatus(OrderStatus.CANCELED);
//            ordersRepository.save(order);
//
//            // ✅ Foydalanuvchi ma'lumotlarini olish
//            String userName = order.getUserName();
//            String userPhone = order.getUserPhone();
//            String orderDetails = order.getOrderDetails();
//
//            if (userName == null || userPhone == null) {
//                sendMessage(chatId, "❌ Xatolik: Foydalanuvchi ma'lumotlari topilmadi.");
//                return;
//            }
//
//            // ✅ Hisobot botiga buyurtmani yuborish
//            sendMessage(HISOBOT_BOT_CHAT_ID, "⚠️ **Buyurtma bekor qilindi!**\n\n" +
//                    "🆔 Buyurtma ID: " + orderId + "\n" +
//                    "👤 **Ism:** " + userName + "\n" +
//                    "📞 **Telefon:** " + userPhone + "\n" +
//                    "🍽 *Buyurtma tafsilotlari:* \n" + orderDetails + "\n\n" +
//                    "🔴 **Admin bilan bog‘laning!**");
//
//            // ✅ Oshxona chatiga xabar berish
//            sendMessage(chatId, "✅ Buyurtma bekor qilindi va admin botiga yuborildi.");
//        }


//        if (update.hasCallbackQuery()) {
//            if (callbackData.startsWith("MARK_SOLD_")) {
//                long orderId = Long.parseLong(callbackData.split("_")[2]);
//                System.out.println("🛠 Sotilgan buyurtma ID: " + orderId);
//
//                Orders order = ordersRepository.findById(orderId).orElse(null);
//                if (order == null) {
//                    sendMessage(chatId, "❌ Buyurtma topilmadi.");
//                    return;
//                }
//
//                chatId = order.getChatId(); // Mijozning chat ID sini olamiz
//
//                // ✅ Mijozning barcha "ORDERED" statusdagi buyurtmalarini olish
//                List<Orders> clientOrders = ordersRepository.findByChatIdAndStatus(chatId, OrderStatus.ORDERED);
//                if (clientOrders.isEmpty()) {
//                    sendMessage(chatId, "❌ Mijozda tasdiqlanmagan buyurtmalar yo‘q.");
//                    return;
//                }
//
//                // ✅ Sotilgan buyurtmalar ro‘yxatini yig‘ish
//                StringBuilder soldOrdersList = new StringBuilder("✅ Quyidagi buyurtmalar sotildi:\n\n");
//
//                for (Orders clientOrder : clientOrders) {
//                    clientOrder.setStatus(OrderStatus.SOLD);
//                    ordersRepository.save(clientOrder);
//
//                    System.out.println("✅ Buyurtma SOLD statusiga o‘tdi: ID = " + clientOrder.getId());
//                }
//
//                sendMessage(groupId, soldOrdersList.toString()); // 🔹 Guruhga sotilgan buyurtmalarni chiqarish
//            }
//        }


        else if (callbackData.startsWith("FOOD_")) {
            long foodId = Long.parseLong(callbackData.split("_")[1]);
            currentOrder.put(chatId, foodId);
            showOrderDetails(chatId, foodId, messageId, 0);
        } else if (callbackData.startsWith("ADD_QUANTITY_")) {
            long foodId = Long.parseLong(callbackData.split("_")[2]);
            updateOrder(chatId, foodId, messageId, 1); // Miqdorni oshirish
        } else if (callbackData.startsWith("REMOVE_QUANTITY_")) {
            long foodId = Long.parseLong(callbackData.split("_")[2]);
            updateOrder(chatId, foodId, messageId, -1); // Miqdorni kamaytirish
        } else if (callbackData.startsWith("ADD_TO_BASKET_")) {
            finalizeOrder(chatId);

        } else if (callbackData.equals("CONFIRM_ORDER")) {
            // Eski manzil va vaqtni tozalash
            Client client = clientRepository.findById(chatId).orElse(null);
            assert client != null;
            client.setPickupTime(null);
            clientRepository.save(client);
            sendInlineKeyboard(chatId, "Buyurtmani qanday olmoqchisiz? Dastavka keyinroq qo'shiladi)", new String[]{"🏠 Borib olib ketish"}, new String[]{"PICKUP"});
//        }else if (callbackData.equals("CONFIRM_ORDER")) {
//            // Eski manzil va vaqtni tozalash
//            Client client = clientRepository.findById(chatId).orElse(null);
//            client.setDeliveryAddress(null);
//            client.setPickupTime(null);
//            clientRepository.save(client);
//            sendInlineKeyboard(chatId, "Buyurtmani qanday olmoqchisiz?",
//                    new String[]{"🚗 Dastavka", "🏠 Borib olib ketish"},
//                    new String[]{"DELIVERY", "PICKUP"});
//        } else if (callbackData.equals("DELIVERY")) {
//            sendMessage(chatId, "Manzilni kiriing iltimos: ");
//            awaitingAddress.put(chatId, true);
        } else if (callbackData.equals("PICKUP")) {
            sendInlineKeyboard(chatId, "Qachon olib ketmoqchisiz?", new String[]{"15 daqiqa", "30 daqiqa", "1 soat", "Vaqtni o‘zingiz kiriting"}, new String[]{"15daqiqa", "30daqiqa", "1soat", "CUSTOM_TIME"});
        } else if (callbackData.equals("CUSTOM_TIME")) {
            sendMessages(chatId, "Iltimos, necha daqiqadan keyin olib ketishingizni yozing: (daqiqa yoki soat ???)");
            awaitingPickupTime.put(chatId, true);
        } else if (callbackData.equals("15daqiqa")) {
            Client client = clientRepository.findById(chatId).orElse(null);
            client.setPickupTime(callbackData);
            clientRepository.save(client);
            confirmOrder(chatId);
        } else if (callbackData.equals("30daqiqa")) {
            Client client = clientRepository.findById(chatId).orElse(null);
            client.setPickupTime(callbackData);
            clientRepository.save(client);
            confirmOrder(chatId);
        } else if (callbackData.equals("1soat")) {
            Client client = clientRepository.findById(chatId).orElse(null);
            client.setPickupTime(callbackData);
            clientRepository.save(client);
            confirmOrder(chatId);
        } else if (callbackData.equals("CLEAR_BASKET")) {
            clearBasket(chatId);
        } else if (callbackData.equals("SHOW_MENU")) {
            showMenus(chatId);
        } else if (callbackData.startsWith("VIEW_BASKET")) {
            showBasket(chatId);
        } else if (callbackData.equals("BACK_TO_MAIN_MENU")) {
            sendMainMenu(chatId);
        } else if (callbackData.equals("SHOW_ORDERS_MENU")) {
            showMenu(chatId);
        } else if (callbackData.equals("UPDATE_BASKET")) {
            updateBasket(chatId);
        } else if (callbackData.equals("RETURN_BACK_TO_SHOW_MENU")) {
            showMenus(chatId); // Menyuga qaytish
        }
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }


    private void sendMessageWithMarkup(String chatId, String text, InlineKeyboardMarkup markup) {
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

}
