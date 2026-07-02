# CazéTV Ao vivo de verdade ⚽⚡

App Android (smartphone + TV) que renderiza lives do YouTube com **atraso mínimo**, usando um WebView com engine de catch-up adaptativa.

## Como funciona

1. Carrega o canal @cazetv e lista as lives ativas
2. Ao selecionar uma live, abre um WebView com a página `youtube.com/watch?v=...`
3. Injeta uma engine JavaScript que acelera o playback baseado no buffer:

```
buffer >= threshold → acelera (ignora isAtLiveHead)
buffer < threshold  → 1.0x (deixa o buffer encher)
```

### Engine

- Usa `document.getElementById('movie_player').setPlaybackRate()` — funciona com streams SABR/manifestless modernas
- Lê latência e buffer via `getStatsForNerds()`, progresso via `getProgressState()`
- **Tick rate**: 250ms (4x/segundo)
- **Buffer threshold auto-ajustado**: 2-6s baseado no `latencyClass` da stream
- **Skip threshold safety net**: se o delay ultrapassar 30s, força seek ao vivo
- **3 modos**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x — padrão)
- **Banner**: ⚡ Acelerando / ✅ Ao vivo / ⏳ Aguardando (sem exibir velocidades)
- **Tela ligada** durante a transmissão (`FLAG_KEEP_SCREEN_ON`)
- **Landscape forçado** na tela de transmissão

### Player (tela de transmissão)

- Overlay superior com botões de modo + TELA CHEIA
- Banner de status no topo
- Faixa branca com logotipo no rodapé (clique para contato: Instagram / WhatsApp)

### Android TV

- Suporte nativo via Leanback (`TvMainActivity`)
- Grade de streams navegável por D-pad
- Loading, erro e retry completos
- Mesma engine de catch-up do smartphone

## Build

```
./gradlew assembleRelease
```

APK assinado em `app/build/outputs/apk/release/app-release.apk`.

## Tecnologias

- Kotlin + Jetpack Compose (smartphone)
- Kotlin + Leanback (Android TV)
- Android WebView (player)
- NewPipe Extractor (descoberta de streams)
- Gradle KTS + version catalog
