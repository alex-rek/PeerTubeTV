# PeerTube для Android TV и планшетов

Исходный проект Android Studio на Kotlin и Jetpack Compose. Есть навигация по основным разделам, лента и поиск через PeerTube API v1, выбор сервера, экран видео на Android Media3/ExoPlayer, история и избранное.

## Сборка APK

1. Установите Android Studio (Hedgehog или новее), JDK 17 и Android SDK Platform 35.
2. Откройте папку `PeerTubeTV` в Android Studio и дождитесь синхронизации Gradle.
3. Для debug APK выберите **Build → Build Bundle(s) / APK(s) → Build APK(s)** или выполните в терминале проекта:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

4. APK появится в `app\build\outputs\apk\debug\app-debug.apk`.

Команда через Gradle wrapper требует wrapper-файлы. Если их нет в проекте, сначала выполните `gradle wrapper --gradle-version 8.9`, затем `.\gradlew.bat assembleDebug`; либо собирайте через Android Studio.

Для своего сервера укажите базовый URL, например `https://peertube.example`. Инстанс должен быть доступен по HTTPS и предоставлять API v1. Проект использует публичный API без входа в аккаунт.

## Примечания по текущей версии

Это стартовая реализация клиента. История и избранное пока хранятся в памяти до закрытия процесса; для полноценного выпуска их следует перенести в Room/DataStore. Реализация входа, подписок, продолжения просмотра и детальной адаптации раскладки для пульта/DPAD также оставлена следующим этапом. Публичные серверы PeerTube могут различаться по доступности и форматам видео.
