package com.example.videoloaderbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Точка входа в Spring Boot приложение для Telegram-бота.
 * Регистрирует бота с использованием токена и имени из application.properties.
 */
@SpringBootApplication
public class VideoLoaderBotApplication {

    // Значение из application.properties (инжектируются автоматически)
    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    public static void main(String[] args) {
        SpringApplication.run(VideoLoaderBotApplication.class, args);
    }

    /**
     * Bean для регистрации бота в TelegramBotsApi.
     * Создает экземпляр MyTelegramBot и регистрирует его для polling.
     */
    @Bean
    public LongPollingBot myTelegramBot() {
        MyTelegramBot bot = new MyTelegramBot(botUsername, botToken);
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            System.out.println("Бот успешно зарегистрирован и запущен: " + botUsername);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }
        return bot;
    }
}