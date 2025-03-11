package com.example.demo.config;

import com.example.demo.service.SalesReportBot;
import com.example.demo.service.TelegramBot;
import com.example.demo.service.ClientBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotInitializer {

    private final TelegramBot adminBot;

    private final ClientBot clientBot;

    private final SalesReportBot salesReportBot;

    public BotInitializer(SalesReportBot salesReportBot,ClientBot clientBot,TelegramBot adminBot) {
        this.salesReportBot = salesReportBot;
        this.clientBot = clientBot;
        this.adminBot = adminBot;
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            botsApi.registerBot(adminBot);
            botsApi.registerBot(clientBot);
            botsApi.registerBot(salesReportBot);
            System.out.println("Three bots successfully registered!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
