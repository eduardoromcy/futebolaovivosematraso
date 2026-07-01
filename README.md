# Futebol Ao Vivo Sem Atraso ⚽⚡

App Android que renderiza lives do YouTube com **atraso mínimo**, usando um WebView com engine de catch-up automático.

## Como funciona

1. Carrega o canal configurado (@cazetv por padrão) e lista as lives ativas
2. Ao selecionar uma live, abre um WebView com a página `youtube.com/watch?v=...`
3. Injeta uma engine JavaScript que acelera o playback em ciclos:

```
accel (velocidade máxima) → rest (1.0x + seek ao vivo) → accel → rest → ...
```

### Engine

- Usa `document.getElementById('movie_player').setPlaybackRate()` — funciona com streams SABR/manifestless modernas
- Lê latência e buffer via `getStatsForNerds()`, progresso via `getProgressState()`
- **Auto-adjust**: detecta `latencyClass` da stream (ULTRA_LOW, LOW, normal) e ajusta duração dos ciclos
- **Skip threshold safety net**: se o delay ultrapassar 30s durante aceleração, força seek ao vivo
- **3 modos de velocidade**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x)

## Build

```
./gradlew assembleRelease
```

O APK assinado estará em `app/build/outputs/apk/release/app-release.apk`.

## Tecnologias

- Kotlin + Jetpack Compose (tela principal)
- Android WebView (player)
- NewPipe Extractor (descoberta de streams)
- Gradle KTS + version catalog
