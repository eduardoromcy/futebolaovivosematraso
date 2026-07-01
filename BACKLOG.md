# BACKLOG

## Implementado

### WebView + Engine (PlayerActivity.kt)

- [x] WebView com `youtube.com/watch?v=...` — desktop UA (Windows Chrome 125)
- [x] Engine JS com estratégia **buffer-based pura** (igual smooth/aggressive mode da extensão):
  - buffer >= threshold(`segduration` × 2) → acelera na velocidade do modo
  - buffer < threshold → 1.0x (deixa buffer encher)
  - **isAtLiveHead é ignorado** para decisão de velocidade (só informativo)
- [x] `setPlaybackRate()` via `document.getElementById('movie_player')` (funciona com SABR/manifestless)
- [x] Leitura de `getStatsForNerds()` (live_latency_secs, buffer_health_seconds)
- [x] Leitura de `getProgressState()` (isAtLiveHead, seekableEnd, current)
- [x] **Buffer threshold auto-ajustado**: `max(2, min(6, segduration × 2))`
- [x] **3 modos de velocidade**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x)
- [x] Seletor de modo no overlay superior (botões)
- [x] Banner de status com 2 estados: ⚡ Acelerando, ⏳ Aguardando buffer
- [x] **Estimativa de chegada**: mostra "chegando ~Xs" durante aceleração
- [x] **Tick rate 250ms** (4x/segundo)
- [x] **Skip threshold safety net**: se delay > 30s, seek ao vivo
- [x] `hardwareAccelerated=true` + try/catch em todo JS (evita tela verde/crashes)
- [x] `webViewGone` flag + lifecycle management (`onPause`/`onResume`/`onDestroy`)
- [x] `setSpeed(1.0)` no `onPause`

### Tela Principal Smartphone (MainActivity.kt)

- [x] Lista de streams via Jetpack Compose + LazyColumn
- [x] Pull-to-refresh (`PullToRefreshBox`) na lista
- [x] Card por stream com: indicador vermelho, título, view count, badge "⚡0 ATRASO"
- [x] Dialog para URL customizada
- [x] Loading spinner + estado de erro com retry

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
| **Modos** | 3 fixos (1.1x / 1.25x / 1.5x) | 1 configurável (1.05x–16x) |
| **Respeitar manual** | ❌ | ✅ só se speed = 1.0 |
| **Indicadores** | Banner no topo | Botões na barra YouTube |
| **Stream ended** | ❌ | ✅ detecta |
| **Timestamp copy** | ❌ | ✅ |
| **Persistence** | ❌ | ✅ chrome.storage |
| **Android TV** | ✅ Leanback nativo | ❌ extensão Chrome |

### Resumo

Nossa engine é **praticamente idêntica** à extensão agora: ambas usam buffer-based com threshold auto-ajustado por `segduration`. Nossa vantagem principal é usar `player.setPlaybackRate()` que funciona com SABR, enquanto a extensão usa `video.playbackRate` que é ignorado pelo YouTube moderno. Também temos suporte a Android TV que a extensão não tem.

## Sugestões futuras

### Prioridade média

1. **Stream ended detection** — detectar se o `seekableEnd` parou de mudar (live acabou) e desligar a engine.

2. **Respeitar controle manual (toggle)** — adicionar opção que, se ativada, só acelera se o usuário não mexeu na velocidade manualmente (similar à extensão).

3. **Persistir modo selecionado** — salvar Suave/Equil/Agressivo no `SharedPreferences` e restaurar na próxima abertura.

### Prioridade baixa

4. **Indicadores no player YouTube** — injetar botões de speed/latência na barra do YouTube.

5. **Múltiplos canais** — permitir configurar mais de um canal e alternar entre eles.

6. **Notificação persistente** — mostrar status atual (modo, latência) numa notificação da barra de status.

## Próximos passos

1. Testar em live real: verificar se a aceleração acontece consistentemente
2. Se a engine não encontrar `movie_player` (Shadow DOM), adicionar fallback com `querySelector`
3. Ajustar threshold ou safety net baseado em testes reais
