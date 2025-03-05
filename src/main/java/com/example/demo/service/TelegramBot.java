package com.example.demo.service;

import com.example.demo.config.BotConfig;
import com.example.demo.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserStateRepository userStateRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private ClientRepository clientRepository;

    final BotConfig config;

    @Autowired
    private ClientBot clientBot;

    public TelegramBot(BotConfig config) {
        this.config = config;

        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "botni boshlash"));
        listofCommands.add(new BotCommand("/yordam", "yordam olish"));

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

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            List<Client> clients = clientRepository.findAll();
            for (Client client : clients) {
                System.out.println("Mijoz chatId: " + client.getChatId());
            }
            UserState userState = userStateRepository.findById(chatId).orElseGet(() -> {
                UserState newState = new UserState();
                newState.setChatId(chatId);
                newState.setState(null);
                return newState;
            });

            switch (message) {
                case "/start":
                case "Start":
                    sendMessage(chatId, "Hush kelibsiz, Botdan haqida bilish uchun '/yordam' tugmasini bosing. ");
                    break;

                case "/yordam":
                case "yordam":

                    sendMessages(chatId, "shulardan foydalaning\n" +
                            "/Menu_kiritish - yangi menu qo'shish\n" +
                            "/Menular_bilan_tanishish - menular ro'yhatini ko'rish\n" +
                            "/Menudan_olish - menularni raqami bo'yicha o'chirish\n" +
                            "/Menuni_yangilash - menuni raqami bo'yicha yangilash\n" +
                            "/barchasini_tozalash - menudagi barcha malumotlarni o'chirish");
                    break;
                case "/Menu_kiritish":
                case "Menu kiritish":

                    userState.setState("WAITING_FOR_NAME");
                    userStateRepository.save(userState);

                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(String.valueOf(chatId));
                    sendMessage.setText("Ovqat nomini kiriting");
                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                    break;

                case "/Menular_bilan_tanishish":
                case "Menular bilan tanishish":
                    List<Menu> menus = menuRepository.findAll();
                    if (menus.isEmpty()) {
                        sendMessage(chatId, "Hozrda ovqatlar ro'yxati kiritilmagan, Iltimos avval ro'yhat qo'shing: ");
                    } else {
                        StringBuilder response = new StringBuilder("Menular royhati\n");
                        for (Menu menu : menus) {
                            response.append(menu.getId())
                                    .append(". ").append(menu.getFood_name())
                                    .append("    ").append(menu.getFood_price())
                                    .append(" so'm   ->>   ").append(menu.getIsActive())
                                    .append("\n");
                        }
                        sendMessage(chatId, response.toString());
                    }
                    break;

                case "/Menudan_olish":
                case "Menudan olish":
                    userState.setState("WAITING_FOR_DELETE_ID");
                    userStateRepository.save(userState);
                    sendMessage(chatId, "Menudagi ovqatni o'chirish uchun raqam yuboring: ");
                    break;

                case "/Menuni_yangilash":
                case "Menuni yangilash":
                    if (menuRepository.count() == 0) {
                        sendMessage(chatId, "ro'yhat bo'm bo'sh Iltimos avval menu qo'shing. 'menu_qo'shish' ");
                        return;
                    }

                    userState.setState("WAITING_FOR_UPDATE_ID");
                    userStateRepository.save(userState);

                    SendMessage sendMessage1 = new SendMessage();
                    sendMessage1.setChatId(String.valueOf(chatId));
                    sendMessage1.setText("Iltimos raqam yuboring, qaysi menuni yangilamoqchisiz.");
                    try {
                        execute(sendMessage1);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                    break;
//                    sendMessages(chatId, "Iltimos raqam yuboring, qaysi menuni yangilamoqchisiz.");


                case "/Barchasini_tozalash":
                case "Barchasini tozalash":
                    userState.setState("WAITING_FOR_CLEAR_CONFIRMATION");
                    userStateRepository.save(userState);
                    sendMessages(chatId, "Rostan ham barcha mahsulotlarni o‘chirmoqchimisiz? 'Ha' deb yozing.");
                    break;

                case "Ha":
                    if ("WAITING_FOR_CLEAR_CONFIRMATION".equals(userState.getState())) {
                        userState.setState("WAITING_FOR_CLEAR_PASSWORD");
                        userStateRepository.save(userState);
                        sendMessages(chatId, "Iltimos, tasdiqlash parolini kiriting:");
                    }
                    break;

                case "12345": // 🔑 Parolni bazadan olish yaxshiroq
                    if ("WAITING_FOR_CLEAR_PASSWORD".equals(userState.getState())) {
                        resetMenu(chatId);
                        sendMessage(chatId, "✅ Barcha mahsulotlar o‘chirildi.");
                        userState.setState(null);
                        userStateRepository.save(userState);
                    } else {
                        sendMessages(chatId, "❌ Noto‘g‘ri parol! O‘chirish bekor qilindi.");
                    }
                    break;

                case "Jarayondami":
                    List<Menu> menuss = menuRepository.findAll();
                    if (menuss.isEmpty()) {
                        sendMessage(chatId, "Hozrda ovqatlar ro'yxati kiritilmagan, Iltimos avval ro'yhat qo'shing: ");
                    } else {
                        StringBuilder response = new StringBuilder("Menular royhati\n");
                        for (Menu menu : menuss) {
                            response.append(menu.getId())
                                    .append(". ").append(menu.getFood_name())
                                    .append("    ").append(menu.getFood_price())
                                    .append(" so'm   ->>   ").append(menu.getIsActive())
                                    .append("\n");
                        }
                        sendMessage(chatId, response.toString());
                    }

                    sendProcessMenu(chatId);
                    break;

                case "Ulashish":
                    ulashishOrder(chatId);
                    break;
                default:
                    handleState(chatId, userState, message);
                    break;
            }
        }
        else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery().getData(), update.getCallbackQuery().getMessage().getChatId());
        }
    }

    private void ulashishOrder(long chatId) {
        List<Menu> menus = menuRepository.findByIsActive("Sotilmoqda");
        if (menus.isEmpty()) {
            sendMessage(chatId, "Hozirda menyu bo'sh. Avval menyu qo'shing.");
            return;
        }

        StringBuilder menuMessage = new StringBuilder("Bugungi menyu:\n");
        for (Menu menu : menus) {
            menuMessage.append(menu.getId()).append(". ").append(menu.getFood_name())
                    .append(" - ").append(menu.getFood_price()).append(" so'm\n");
        }

        clientBot.broadcastMessageToClients(menuMessage.toString());

        sendMessage(chatId, "Menyu barcha mijozlarga muvaffaqiyatli yuborildi.");
    }

    private void sendMainMenu(long chatId) {

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("Jarayondami");
        button.setCallbackData("PROCESS_STATUS");
        row1.add(button);

        rows.add(row1);
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Asosiy menyu:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleCallback(String callbackData, long chatId) {
        switch (callbackData) {
            case "VIEW_ACTIVE":
                showActiveMenus(chatId);
                break;

            case "VIEW_INACTIVE":
                showInactiveMenus(chatId);
                break;

            case "BACK_TO_MAIN":
                sendMessage(chatId, "O'tkazish tugatildi! Chatdagi tugmalarda foydalanishingiz mumkin");
                break;

            default:
                sendMessage(chatId, "Noma'lum amal.");
                break;
        }
    }

    private void sendProcessMenu(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton activeButton = new InlineKeyboardButton();
        activeButton.setText("Sotilayotgan"); // Sotuvdagi mahsulotlar
        activeButton.setCallbackData("VIEW_ACTIVE");
        row1.add(activeButton);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton inactiveButton = new InlineKeyboardButton();
        inactiveButton.setText("Sotuvda yo'q"); // Sotuvdan chiqarilgan mahsulotlar
        inactiveButton.setCallbackData("VIEW_INACTIVE");
        row2.add(inactiveButton);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Orqaga"); // Asosiy menyuga qaytish
        backButton.setCallbackData("BACK_TO_MAIN");
        row3.add(backButton);

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Jarayon holatini tanlang:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showActiveMenus(long chatId) {
        List<Menu> activeMenus = menuRepository.findByIsActive("Sotilmoqda");
        if (activeMenus.isEmpty()) {
            sendMessage(chatId, "Hozirda sotuvda mahsulotlar yo'q.");
        } else {
            StringBuilder response = new StringBuilder("Sotuvdagi mahsulotlar:\n\n");
            for (Menu menu : activeMenus) {
                response.append(menu.getId())
                        .append(". ").append(menu.getFood_name())
                        .append(" - ").append(menu.getFood_price()).append(" so'm\n");
            }
            response.append("\nQaysi mahsulotni sotuvdan chiqarishni xohlaysiz? Raqamini yuboring yoki \"Orqaga\" tugmasini bosing.");

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(response.toString());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Orqaga");
            backButton.setCallbackData("BACK_TO_MAIN");
            row1.add(backButton);
            rows.add(row1);

            markup.setKeyboard(rows);
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            UserState userState = userStateRepository.findById(chatId).orElse(new UserState(chatId));
            userState.setState("WAITING_FOR_MULTIPLE_ACTIVE_TO_INACTIVE");
            userStateRepository.save(userState);
        }
    }

    private void showInactiveMenus(long chatId) {

        List<Menu> inactiveMenus = menuRepository.findByIsActive("Sotuvda yo'q");
        if (inactiveMenus.isEmpty()) {
            sendMessage(chatId, "Hozirda sotuvda yo'q mahsulotlar yo'q.");
        } else {
            StringBuilder response = new StringBuilder("Sotuvda yo'q mahsulotlar:\n\n");
            for (Menu menu : inactiveMenus) {
                response.append(menu.getId())
                        .append(". ").append(menu.getFood_name())
                        .append(" - ").append(menu.getFood_price()).append(" so'm\n");
            }
            response.append("\nQaysi menyuni sotuvga qo'yishni xohlaysiz? Raqamini yuboring yoki \"Orqaga\" tugmasini bosing.");

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(response.toString());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Orqaga");
            backButton.setCallbackData("BACK_TO_MAIN");
            row1.add(backButton);
            rows.add(row1);

            markup.setKeyboard(rows);
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            UserState userState = userStateRepository.findById(chatId).orElse(new UserState(chatId));
            userState.setState("WAITING_FOR_MULTIPLE_INACTIVE_TO_ACTIVE");
            userStateRepository.save(userState);
        }
    }

    private void handleState(long chatId, UserState userState, String message) {

        switch (userState.getState()) {
            case "WAITING_FOR_MULTIPLE_ACTIVE_TO_INACTIVE":
                if ("Orqaga".equalsIgnoreCase(message)) {
                    userState.setState(null);
                    userStateRepository.save(userState);
                    sendMainMenu(chatId);
                    return;
                }

                try {
                    long menuId = Long.parseLong(message);
                    Menu menu = menuRepository.findById(menuId).orElse(null);

                    if (menu == null || !"Sotilmoqda".equals(menu.getIsActive())) {
                        sendMessage(chatId, "Noto'g'ri ID yoki menyu allaqachon sotuvda yo'q.");
                    } else {
                        menu.setIsActive("Sotuvda yo'q");
                        menuRepository.save(menu);
                        sendMessage(chatId, "Menyu sotuvdan chiqarildi: " + menu.getFood_name());
                    }
                    showActiveMenus(chatId);

                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Iltimos, to'g'ri raqam yuboring yoki \"Orqaga\" tugmasini bosing.");
                }
                break;

            case "WAITING_FOR_MULTIPLE_INACTIVE_TO_ACTIVE":
                if ("Orqaga".equalsIgnoreCase(message)) {
                    // Asosiy menyuga qaytish
                    userState.setState(null);
                    userStateRepository.save(userState);
                    sendMainMenu(chatId);
                    return;
                }

                try {
                    long menuId = Long.parseLong(message);
                    Menu menu = menuRepository.findById(menuId).orElse(null);

                    if (menu == null || !"Sotuvda yo'q".equals(menu.getIsActive())) {
                        sendMessage(chatId, "Noto'g'ri ID yoki menyu allaqachon sotuvda.");
                    } else {
                        menu.setIsActive("Sotilmoqda");
                        menuRepository.save(menu);
                        sendMessage(chatId, "Menyu sotuvga o'tkazildi: " + menu.getFood_name());
                    }

                    showInactiveMenus(chatId);

                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Iltimos, to'g'ri raqam yuboring yoki \"Orqaga\" tugmasini bosing.");
                }
                break;
        }

        if ("WAITING_FOR_NAME".equals(userState.getState())) {
            userState.setFoodName(message);
            userState.setState("WAITING_FOR_PRICE");
            userStateRepository.save(userState);
            sendMessage(chatId, "Ovqat narxini kiriting");
        } else if ("WAITING_FOR_PRICE".equals(userState.getState())) {
            try {
                double price = Double.parseDouble(message);

                Menu menu = new Menu();
                menu.setFood_name(userState.getFoodName());
                menu.setFood_price(price);
                menu.setIsActive("Sotuvda yo'q");
                menuRepository.save(menu);

                userStateRepository.delete(userState);
                sendMessage(chatId, "menu muvvofaqiyatli qo'shildi\n" +
                        "Nomi: " + menu.getFood_name() + "\n" +
                        "Narxi: " + menu.getFood_price() + "\n" +
                        "Jarayonda?: " + menu.getIsActive());
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Iltimos narxni 1000 tariqa kiriting");
            }

        } else if ("WAITING_FOR_DELETE_ID".equals(userState.getState())) {
            try {
                Long id = Long.parseLong(message);
                menuRepository.deleteById(id);
                sendMessage(chatId, "Ro'yxatdan bu raqam " + id + " o'chirildi.");
                userStateRepository.delete(userState);
            } catch (Exception e) {
                sendMessage(chatId, "Xato kiritildi yoki bu raqam ro'yxatda mavjud emas");
            }

        } else if ("WAITING_FOR_UPDATE_ID".equals(userState.getState())) {
            try {
                Long id = Long.parseLong(message);
                if (menuRepository.existsById(id)) {
                    userState.setFoodName(id.toString());
                    userState.setState("CHOOSE_UPDATE_OPTION");
                    userStateRepository.save(userState);
                    sendUpdateOptions(chatId);
                } else {
                    sendMessage(chatId, "Ro'yxatdagi bu raqam " + id + " topilmadi");
                }
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Habarni xato kiritdiz. Iltimos to'g'ri raqam yuboring!");
            }

        } else if ("CHOOSE_UPDATE_OPTION".equals(userState.getState())) {
            switch (message) {
                case "Nomni yangilash":
                    userState.setState("WAITING_FOR_UPDATE_NAME");
                    userStateRepository.save(userState);
                    sendMessage(chatId, "Qanday nomga yangilamoqchisiz, Iltimos ovqat nomini yozing: ");
                    break;

                case "Narxni yangilash":
                    userState.setState("WAITING_FOR_UPDATE_PRICE");
                    userStateRepository.save(userState);
                    sendMessage(chatId, "Iltimos endi yangi narxni kiriting: ");
                    break;

                case "Hammasini yangilash":
                    userState.setState("WAITING_FOR_UPDATE_ALL");
                    userStateRepository.save(userState);
                    sendMessage(chatId, "Iltimos yangi nomni yuboring");
                    break;

                default:
                    sendMessage(chatId, "Xato kirituv. Iltimos shularni tanlang: 'nomni yangilash', 'narxni yanglash', 'hammasini yangilash");
            }

        } else if ("WAITING_FOR_UPDATE_NAME".equals(userState.getState())) {
            Long id = Long.parseLong(userState.getFoodName());
            Menu menu = menuRepository.findById(id).orElse(null);
            if (menu != null) {
                menu.setFood_name(message);
                menuRepository.save(menu);
                sendMessage(chatId, "Ovqat nomi muvoffaqiyatli yangilandi");
            } else {
                sendMessage(chatId, "Ro'yxatdan bu raqamdagi ovqat topilmadi");
            }
            userStateRepository.delete(userState);

        } else if ("WAITING_FOR_UPDATE_PRICE".equals(userState.getState())) {
            Long id = Long.parseLong(userState.getFoodName());
            Menu menu = menuRepository.findById(id).orElse(null);
            if (menu != null) {
                try {
                    double price = Double.parseDouble(message);
                    menu.setFood_price(price);
                    menuRepository.save(menu);
                    sendMessage(chatId, "Narx muvoffaqiyatni yangilandi");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Xato ko'rinishidagi xabar, Iltimos to'g'ri shalkda yuboring");
                }
            } else {
                sendMessage(chatId, "Bu nom topilmadi");
            }
            userStateRepository.delete(userState);

        } else if ("WAITING_FOR_UPDATE_ALL".equals(userState.getState())) {
        Long id = Long.parseLong(userState.getFoodName());
        Menu menu = menuRepository.findById(id).orElse(null);
        if (menu != null) {
            menu.setFood_name(message);
            menuRepository.save(menu); // Save the updated name immediately
            userState.setState("WAITING_FOR_NEW_PRICE_AFTER_NAME");
            userStateRepository.save(userState);
            sendMessage(chatId, "Ovqat nomi muvoffiqiyatli saqlandi endi uning narxini yuboring: ");
        } else {
            sendMessage(chatId, "Bu nom topilmadi.");
            userStateRepository.delete(userState);
        }
        } else if ("WAITING_FOR_NEW_PRICE_AFTER_NAME".equals(userState.getState())) {
            Long id = Long.parseLong(userState.getFoodName());
            Menu menu = menuRepository.findById(id).orElse(null);
            if (menu != null) {
                try {
                    double price = Double.parseDouble(message);
                    menu.setFood_price(price);
                    menuRepository.save(menu); // Save both name and price now
                    sendMessage(chatId, "Ovqat nomi va narxi muvoffiqiyatli yangilandi.");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Xato raqam kiritildi Iltimos to'g'ri raqam kiriting");
                }
            } else {
                sendMessage(chatId, "Bu nom topilmadi");
            }
            userStateRepository.delete(userState);
        }
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("Menu kiritish");
        row1.add("Menular bilan tanishish");
        row1.add("Menudan olish");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("Menuni yangilash");
        row2.add("Barchasini tozalash");
        row2.add("Jarayondami");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("Ulashish");

        keyboardRows.add(row1);
        keyboardRows.add(row2);
        keyboardRows.add(row3);

        keyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendUpdateOptions(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("Nimani yangilashni hohlaysiz");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        // 3 ta tugma qo'shish
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Nomni yangilash");
        row.add("Narxni yangilash");
        row.add("Hammasini yangilash");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void resetMenu(long chatId) {
        try {
            menuRepository.deleteAll(); // Remove all menu items
            sendMessage(chatId, "Ro'yxatdagi barcha malumotlar o'chirildi va u boshidan qo'shishga tayyor! ");
        } catch (Exception e) {
            sendMessage(chatId, "Boshidan boshlash muvoffaqiyatsizlikka uchradi. Iltimos boshidan harakat qilb ko'ring");
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

}
