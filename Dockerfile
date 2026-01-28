# Используем лёгкий образ с Java 17
FROM eclipse-temurin:17-jre-alpine

# Рабочая директория внутри контейнера
WORKDIR /app

# Копируем собранный jar-файл (будет создан на следующем шаге)
COPY target/video-loader-bot-0.0.1-SNAPSHOT.jar app.jar
# Устанавливаем yt-dlp + ffmpeg (ffmpeg нужен для объединения видео+аудио)
RUN apk add --no-cache \
    python3 \
    py3-pip \
    ffmpeg && \
    pip3 install --no-cache-dir --break-system-packages -U yt-dlp

# Папка, куда будут сохраняться видео, логи и cookies
VOLUME /app/downloads

# Запускаем приложение
CMD ["java", "-jar", "app.jar"]