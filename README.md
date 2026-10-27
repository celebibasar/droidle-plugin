# Droidle — Android Studio Plugin

Daily 5-question Android quiz right inside Android Studio.

## Geliştirme

**Gereksinimler:** JDK 17, internet bağlantısı (Gradle SDK indirir)

```bash
# Plugin'i test IDE'sinde çalıştır
./gradlew runIde

# Dağıtım için .zip oluştur
./gradlew buildPlugin
```

## Yapı

```
src/main/kotlin/app/droidle/plugin/
├── DroidleToolWindowFactory.kt   ← Tool Window kayıt ve routing
├── model/Question.kt             ← API model sınıfları
├── service/DroidleService.kt     ← droidle.app API istemcisi (OkHttp)
├── state/StateManager.kt         ← Streak + PropertiesComponent
├── settings/                     ← Settings > Tools > Droidle
└── ui/
    ├── QuizPanel.kt              ← 5 soruluk quiz akışı
    └── ResultPanel.kt            ← Skor özeti
```

## API

`GET /api/daily-quiz` ve `POST /api/verify-answer` — droidle.app'teki mevcut endpointler.
Plugin tamamen istemci taraflıdır; ayrı bir backend gerektirmez.
