# BACKLOG

## Implementado

### WebView + Engine (PlayerActivity.kt)

- [x] WebView com `youtube.com/watch?v=...` — desktop UA (Windows Chrome 125)
- [x] Engine JS com estratégia **buffer-based pura** (igual smooth mode da extensão):
  - buffer >= threshold(`segduration` × 2) → acelera na velocidade do modo
  - buffer < threshold → 1.0x (deixa buffer encher)
  - **isAtLiveHead é ignorado** para decisão de velocidade
- [x] `setPlaybackRate()` via `document.getElementById('movie_player')` (funciona com SABR/manifestless)
- [x] Leitura de `getStatsForNerds()` (live_latency_secs, buffer_health_seconds)
- [x] Leitura de `getProgressState()` (isAtLiveHead, seekableEnd, current)
- [x] **Buffer threshold auto-ajustado**: `max(2, min(6, segduration × 2))`
- [x] **3 modos de velocidade**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x — padrão)
- [x] Seletor de modo no overlay superior (botões: "Suave", "Equil.", "Agress.")
- [x] Banner de status no topo: ⚡ Acelerando / ✅ Ao vivo / ⏳ Aguardando (sem velocidades)
- [x] **Estimativa de chegada**: mostra "chegando ~Xs" durante aceleração
- [x] **Tick rate 250ms** (4x/segundo)
- [x] **Skip threshold safety net**: se delay > 30s, seek ao vivo
- [x] **Tela ligada** (`FLAG_KEEP_SCREEN_ON`) durante transmissão
- [x] **Landscape forçado** (`sensorLandscape`) na transmissão
- [x] `hardwareAccelerated=true` + try/catch em todo JS
- [x] `webViewGone` flag + lifecycle management (`onPause`/`onResume`/`onDestroy`)
- [x] Fullscreen via `onShowCustomView` + `.ytp-fullscreen-button`
- [x] **Faixa branca** com logotipo no rodapé (clique → contato Instagram/WhatsApp)
- [x] **Botão TELA CHEIA** no overlay

### Tela Principal Smartphone (MainActivity.kt)

- [x] Lista de streams via Jetpack Compose + LazyColumn
- [x] Pull-to-refresh (`PullToRefreshBox`) na lista
- [x] Card por stream com: indicador vermelho, título, view count, badge "⚡0 ATRASO"
- [x] Dialog para URL customizada ("Trocar")
- [x] Loading spinner + estado de erro com retry
- [x] Logotipo no rodapé (clique → contato Instagram/WhatsApp)

### Android TV (TvMainActivity.kt)

- [x] Activity dedicada via Leanback (`VerticalGridView`)
- [x] Grade de streams navegável por D-pad (3 colunas)
- [x] Loading spinner + estado de erro com retry
- [x] View count nos cards da stream
- [x] Manifest com `leanback required=false` (mesmo APK pra phone e TV)

### Scraper (YouTubeChannelScraper.kt)

- [x] `checkIsLive()` filtra streams "esperando/aguardando" (não ao vivo)
- [x] `extractViewCount()` corrigido para português:
  - Remove pontos (separador de milhar): `"6.543.210"` → 6543210
  - Converte vírgula pra ponto decimal: `"1,5 mil"` → 1500
  - Suporte a "mil", "mi", "milhão/milhões"
- [x] `extractViewCountLabel()` retorna label bruta

### Infra

- [x] GitHub: `eduardoromcy/futebolaovivosematraso`
- [x] Nome do app: "CazéTV Ao vivo de verdade"
- [x] Release APK assinado com keystore
- [x] R8 minification ativo
- [x] .gitignore
- [x] README.md + BACKLOG.md (atualizados a cada commit)

## Comparação com live-catch-up extension

Repo de referência: [yudai-tiny-developer/live-catch-up](https://github.com/yudai-tiny-developer/live-catch-up)

| Funcionalidade | Nós | Extensão |
|---|---|---|
| **Estratégia** | Buffer-based: threshold = segdur × 2 | Buffer-based: threshold = segdur × 2 |
| **Playback API** | `player.setPlaybackRate()` ✅ SABR | `video.playbackRate` ❌ |
| **Tick rate** | 250ms ✅ | 250ms |
| **Estimativa** | ✅ "chegando ~Xs" no banner | ✅ "(14:32)" na barra |
| **Safety net seek** | ✅ 30s (fixo) | ✅ 300s (configurável) |
| **Buffer threshold** | auto: segdur × 2 (clamped 2-6s) | auto: segdur × 2 |
| **Modos** | 3 fixos (Suave/Equil./Agress.) | 1 configurável (1.05x–16x) |
| **Respeitar manual** | ❌ | ✅ só se speed = 1.0 |
| **Indicadores** | Banner no topo | Botões na barra YouTube |
| **Stream ended** | ❌ | ✅ detecta |
| **Timestamp copy** | ❌ | ✅ |
| **Persistence** | ❌ | ✅ chrome.storage |
| **Android TV** | ✅ Leanback nativo | ❌ extensão Chrome |
| **Landscape** | ✅ forçado na transmissão | ❌ (extensão Chrome) |
| **Sleep prevention** | ✅ `FLAG_KEEP_SCREEN_ON` | ❌ |

## Sugestões futuras

### Prioridade média

1. **Stream ended detection** — detectar se o `seekableEnd` parou de mudar (live acabou) e desligar a engine.
2. **Respeitar controle manual (toggle)** — adicionar opção que só acelera se o usuário não mexeu na velocidade.
3. **Persistir modo selecionado** — salvar o modo no `SharedPreferences`.

### Prioridade baixa

4. **Indicadores no player YouTube** — injetar botões de speed/latência na barra do YouTube.
5. **Múltiplos canais** — permitir configurar mais de um canal.
6. **Notificação persistente** — mostrar status numa notificação da barra.

## Próximos passos

1. Testar em live real: verificar aceleração consistente
2. Fallback para Shadow DOM se `movie_player` não for encontrado
3. Ajustar threshold/safety net baseado em testes
