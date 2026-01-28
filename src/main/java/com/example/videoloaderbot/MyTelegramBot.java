package com.example.videoloaderbot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram-бот для скачивания видео по ссылке (YouTube, VK, Shorts и др.) с помощью yt-dlp.
 * Работает в Docker, сохраняет файлы в /app/downloads (монтируется как ./downloads на хосте).
 * Ограничение размера ~45 МБ (запас под Telegram-лимит ~50 МБ).
 * Файлы остаются на диске даже после ошибки.
 */
public class MyTelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;

    public MyTelegramBot(String botUsername, String botToken) {
        super();
        this.botUsername = botUsername;
        this.botToken = botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String originalText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Очистка текста от лишних символов
            String text = originalText.trim()
                    .replaceAll("[\\p{C}\\p{Zs}\\p{So}]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            System.out.println("[DEBUG] Получено от " + chatId + ": " + text);

            sendMessage(chatId, "Ты написал: " + originalText);

            Pattern urlPattern = Pattern.compile("(https?://\\S+)");
            Matcher matcher = urlPattern.matcher(text);
            if (matcher.find()) {
                String url = matcher.group(1);
                sendMessage(chatId, "[DEBUG] Ссылка найдена! Запускаю скачивание: " + url);

                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> downloadAndSendVideo(chatId, url));
                executor.shutdown();
            } else {
                sendMessage(chatId, "Не нашёл валидную ссылку! Отправь http/https.");
            }
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Скачивает видео с помощью yt-dlp.
     * Путь сохранения: /app/downloads (монтируется с хоста).
     * Cookies используются только для VK и YouTube Shorts (если файл cookies.txt есть).
     */
    private void downloadAndSendVideo(long chatId, String url) {
        sendMessage(chatId, "[DEBUG] Начинаю скачивание...");

        try {
            String downloadDir = "/app/downloads";
            String fileName = "video_" + System.currentTimeMillis() + ".mp4";
            String outputPath = downloadDir + "/" + fileName;

            sendMessage(chatId, "Скачиваю в: " + outputPath);

            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp",
                    "--verbose",
                    "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
                    "--referer", "https://www.youtube.com/",
                    "-f", "bestvideo[ext=mp4][height<=720]+bestaudio[ext=m4a]/best[ext=mp4][height<=720]",
                    "--max-filesize", "45m",  // запас под Telegram-лимит ~50 МБ
                    "-o", outputPath,
                    "--no-playlist",
                    url
            );

            // Cookies: только для VK и YouTube Shorts (если файл есть в /app/downloads/cookies.txt)
            if (url.contains("vk.com") || url.contains("youtube.com/shorts")) {
                pb.command().addAll(java.util.Arrays.asList("--cookies", "/app/downloads/cookies.txt"));
            }

            pb.directory(new File(downloadDir));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Читаем вывод yt-dlp
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder ytOutput = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                ytOutput.append(line).append("\n");
                System.out.println("[yt-dlp] " + line);
            }

            int exitCode = process.waitFor();

            // Сохраняем лог
            String logFilePath = downloadDir + "/yt-dlp-log_" + System.currentTimeMillis() + ".txt";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath))) {
                writer.write(ytOutput.toString());
            } catch (IOException e) {
                System.err.println("[ERROR] Не удалось записать лог: " + e.getMessage());
            }

            if (exitCode != 0) {
                sendMessage(chatId, "yt-dlp завершился с ошибкой (код " + exitCode + "). Лог: " + logFilePath);
                return;
            }

            File videoFile = new File(outputPath);
            if (!videoFile.exists() || videoFile.length() == 0) {
                sendMessage(chatId, "Файл не найден или пустой: " + outputPath);
                return;
            }

            long fileSizeMB = videoFile.length() / 1024 / 1024;
            sendMessage(chatId, "Видео скачано (" + fileSizeMB + " МБ). Сохранено: " + outputPath);

        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка: " + e.getMessage() + ". Проверь консоль.");
        }
    }
}
