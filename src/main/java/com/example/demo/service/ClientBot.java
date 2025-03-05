package com.example.demo.service;

import com.example.demo.config.ClientBotConfig;
import com.example.demo.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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

import java.util.*;

@Component
public class ClientBot extends TelegramLongPollingBot {

    private final ClientBotConfig config;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private ClientRepository clientRepository;

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

//        if (update.hasMessage() && update.getMessage().hasText()) {
//            chatId = update.getMessage().getChatId();
//            System.out.println("Guruh ID: " + chatId);
//        }


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
                } else if (
                        client.getName() == null
                        || Objects.equals(client.getName(), "Menu")
                        ||Objects.equals(client.getName(), "Savatcha")
                        ||Objects.equals(client.getName(), "Buyurtma berish")) {
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
                    sendMessages(chatId, "shulardan foydalaning\n" +
                            "/Menular_bilan_tanishish - yangi menu qo'shish\n" +
                            "/Savatcha - menular ro'yhatini ko'rish\n" +
                            "/Menudan_olish - menularni raqami bo'yicha o'chirish\n" +
                            "/other - admindan yordam");
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
            menuText.append(menu.getId()).append(". ").append(menu.getFood_name())
                    .append(" - ").append(menu.getFood_price()).append(" so'm\n");
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
            row.add(createButton( menu.getId()+ ". "+ menu.getFood_name() + "  -  " + menu.getFood_price()+ " so'm" , "FOOD_" + menu.getId()));
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

        long userId = getOrCreateUserId(chatId);
        StringBuilder confirmedOrderDetails = new StringBuilder(" ");
        confirmedOrderDetails.append("🆔: ").append(userId).append("\n");

        int totalAmount = 0;

        Client client = clientRepository.findById(chatId).orElse(null);
        String userName = (client != null && client.getName() != null) ? client.getName() : "Foydalanuvchi";
        String userPhone = (client != null && client.getPhoneNumber() != null) ? client.getPhoneNumber() : "Telefon raqami kiritilmagan";

        confirmedOrderDetails.append("🛒 *Buyurtmangiz tasdiqlandi!* \n\n");
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

                confirmedOrderDetails.append("🍔 ").append(menu.getFood_name()).append(" x ").append(quantity)
                        .append(" = ").append(itemTotal).append(" so'm\n");
            }
        }

//        int chegirma = totalAmount/10;
        confirmedOrderDetails.append(String.format("\n💰 Jami narx: %d so'm\n", totalAmount));
//                .append(String.format("Botdan buyurtma qilganingiz uchun chegirma sifatida %d so'm chegirmani qo'lga kiritdingiz\n", chegirma))
//                .append(String.format("\n💰 Yakuniy narx: %d so'm", totalAmount - chegirma));

        sendMessage(chatId, "✅ Buyurtmangiz tasdiqlandi!\n\n" + confirmedOrderDetails.toString());

        String groupMessage = confirmedOrderDetails.toString();
        sendOrderToGroup(groupMessage);

        orders.remove(chatId);

        try {
            // 3 soniya kutish (3000 millisekund)
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            sendMessage(chatId,"Yana buyurtma berishni hohlasangiz tugmalardan foydalaning");
            Thread.currentThread().interrupt(); // Agar thread uzilgan bo'lsa, statusni tiklash
        }
        sendMessage(chatId,"Yana buyurtma berishni hohlasangiz tugmalardan foydalaning");
    }

    private void sendOrderToGroup(String orderDetails) {
        String groupId = "-1002288347120";
        SendMessage message = new SendMessage();
        message.setChatId(groupId);

        message.setText("📦 Yangi buyurtma:\n\n" + orderDetails.trim());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showOrderDetails(long chatId, long foodId, int messageId, int quantity) {

        Menu menu = menuRepository.findById(foodId).orElse(null);
        if (menu == null) {
            sendMessage(chatId, "Ovqat topilmadi.");
            return;
        }

        String details = "Tanlangan mahsulot:\n" +
                "ID: " + menu.getId() + "\n" +
                "Nomi: " + menu.getFood_name() + "\n" +
                "Narxi: " + (menu.getFood_price() * quantity) + " so'm\n" +
                "Miqdor: " + quantity;

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

        int quantity = userBasket.getOrDefault(foodId, 0)+1;
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

                basketDetails.append("ID: ").append(menu.getId())
                        .append("\nNomi: ").append(menu.getFood_name())
                        .append("\nNarxi: ").append(menu.getFood_price()).append(" so'm")
                        .append("\nMiqdor: ").append(quantity)
                        .append("\nJami: ").append(itemTotal).append(" so'm\n\n");
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

                basketDetails.append("🍔 ").append(menu.getFood_name())
                        .append("\nMiqdor: ").append(quantity)
                        .append("\nJami: ").append(itemTotal).append(" so'm\n\n");
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

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        if (callbackData.startsWith("FOOD_")) {
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

        }else if (callbackData.equals("CONFIRM_ORDER")) {
            // Eski manzil va vaqtni tozalash
            Client client = clientRepository.findById(chatId).orElse(null);
            assert client != null;
            client.setPickupTime(null);
            clientRepository.save(client);
            sendInlineKeyboard(chatId, "Buyurtmani qanday olmoqchisiz? Dastavka keyinroq qo'shiladi)",
                    new String[]{"🏠 Borib olib ketish"},
                    new String[]{"PICKUP"});
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
            sendInlineKeyboard(chatId, "Qachon olib ketmoqchisiz?",
                    new String[]{"15 daqiqa", "30 daqiqa", "1 soat", "Vaqtni o‘zingiz kiriting"},
                    new String[]{"15daqiqa", "30daqiqa", "1soat", "CUSTOM_TIME"});
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
        }  else if(callbackData.equals("CLEAR_BASKET")){
            clearBasket(chatId);
        } else if (callbackData.equals("SHOW_MENU")) {
            showMenus(chatId);
        } else if (callbackData.startsWith("VIEW_BASKET")) {
            showBasket(chatId);
        } else if(callbackData.equals("BACK_TO_MAIN_MENU")){
            sendMainMenu(chatId);
        } else if(callbackData.equals("SHOW_ORDERS_MENU")){
            showMenu(chatId);
        } else if(callbackData.equals("UPDATE_BASKET")){
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

}
