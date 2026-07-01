# Futebol Ao Vivo Sem Atraso ⚽⚡

App Android que renderiza lives do YouTube com **atraso mínimo**, usando um WebView com engine de catch-up adaptativa.

## Como funciona

1. Carrega o canal configurado (@cazetv por padrão) e lista as lives ativas
2. Ao selecionar uma live, abre um WebView com a página `youtube.com/watch?v=...`
3. Injeta uma engine JavaScript que acelera o playback baseado no buffer:

```
buffer >= threshold → acelera na velocidade do modo (ignora isAtLiveHead)
buffer < threshold  → 1.0x (deixa o buffer encher)
```

### Engine

- Usa `document.getElementById('movie_player').setPlaybackRate()` — funciona com streams SABR/manifestless modernas
- Lê latência e buffer via `getStatsForNerds()`, progresso via `getProgressState()`
- **Tick rate**: 250ms (4x/segundo)
- **Buffer threshold auto-ajustado**: 2-6s baseado no `latencyClass` da stream (ULTRA_LOW, LOW, normal)
- **Estimativa de chegada**: mostra quantos segundos faltam pra alcançar o ao vivo
- **Skip threshold safety net**: se o delay ultrapassar 30s, força seek ao vivo
- **3 modos de velocidade**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x)
- **2 status no banner**: ⚡ Acelerando, ⏳ Aguardando buffer

## Build

```
./gradlew assembleRelease
```

O APK assinado estará em `app/build/outputs/apk/release/app-release.apk`.

## Tecnologias

- Kotlin + Jetpack Compose (tela principal com pull-to-refresh)
- Android WebView (player)
- NewPipe Extractor (descoberta de streams)
- Gradle KTS + version catalog
