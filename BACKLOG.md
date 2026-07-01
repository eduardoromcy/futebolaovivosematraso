# BACKLOG

## Implementado

### WebView + Engine (PlayerActivity.kt)

- [x] WebView com `youtube.com/watch?v=...` — desktop UA (Windows Chrome 125)
- [x] Engine JS com estratégia **adaptativa** (igual à extensão live-catch-up):
  - buffer < 1.0s → 1.0x (safety)
  - isAtLiveHead → 1.0x
  - atrasado → acelera na velocidade do modo (sem esperar buffer)
- [x] `setPlaybackRate()` via `document.getElementById('movie_player')` (funciona com SABR/manifestless)
- [x] Leitura de `getStatsForNerds()` (live_latency_secs, buffer_health_seconds)
- [x] Leitura de `getProgressState()` (isAtLiveHead, seekableEnd, current)
- [x] **3 modos de velocidade**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x)
- [x] Seletor de modo no overlay superior (botões)
- [x] Banner de status com 3 estados: ⚡ Acelerando, ✅ Ao Vivo, ⏳ Aguardando
- [x] **Estimativa de chegada**: mostra "chegando ~Xs" durante aceleração
- [x] **Tick rate 250ms** (4x/segundo)
- [x] **Skip threshold safety net**: se delay > 30s, seek ao vivo
- [x] `hardwareAccelerated=true` + try/catch em todo JS (evita tela verde/crashes)
- [x] `webViewGone` flag + lifecycle management (`onPause`/`onResume`/`onDestroy`)
- [x] `setSpeed(1.0)` no `onPause`

### Tela Principal (MainActivity.kt)

- [x] Lista de streams via Jetpack Compose + LazyColumn
- [x] Pull-to-refresh (`PullToRefreshBox`) na lista
- [x] Card por stream com: indicador vermelho, título, view count, badge "⚡0 ATRASO"
- [x] Dialog para URL customizada
- [x] Loading spinner + estado de erro com retry

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
| **Estratégia de catch-up** | Adaptativa: atrasado → acelera | Adaptativa: buffer > threshold → acelera |
| **Playback rate API** | `player.setPlaybackRate()` ✅ (funciona SABR) | `video.playbackRate` ❌ (provavelmente ignorado) |
| **Tick rate** | 250ms ✅ | 250ms |
| **Estimativa de chegada** | ✅ "chegando ~Xs" no banner | ✅ "(14:32)" na barra do YouTube |
| **Skip threshold** | ✅ 30s (fixo) | ✅ 300s (configurável) |
| **Buffer safety** | buffer < 1.0s → 1.0x | buffer < threshold → 1.0x |
| **Stats** | `getStatsForNerds()` | `getStatsForNerds()` |
| **Progress** | `getProgressState()` | `getProgressState()` |
| **Respeitar controle manual** | ❌ (deliberado) | ✅ só acelera se speed = 1.0 |
| **Indicadores visuais** | Banner no topo | Botões na barra do YouTube |
| **Modos** | 3 fixos (1.1x / 1.25x / 1.5x) | 1 configurável (1.05x–16x) |
| **Fallback seek** | Apenas safety net (30s) | Configurável (default 300s) |
| **Stream ended detection** | ❌ | ✅ detecta se live acabou |
| **Timestamp copy** | ❌ | ✅ click copia URL |
| **Persistence** | ❌ (reinicia modo padrão) | ✅ chrome.storage |

### Resumo

Nossa engine é **funcionalmente equivalente** na parte central de catch-up. Nossa vantagem principal é usar `player.setPlaybackRate()` que funciona com streams SABR modernas, enquanto a extensão usa `video.playbackRate` que provavelmente é ignorado pelo YouTube.

A extensão ainda tem vantagens em: UI integrada ao player, stream ended detection, configuração persistente.

## Sugestões futuras

### Prioridade média

1. **Stream ended detection** — detectar se o `seekableEnd` parou de mudar (live acabou) e desligar a engine.

2. **Respeitar controle manual (toggle)** — adicionar opção que, se ativada, só acelera se o usuário não mexeu na velocidade manualmente (similar à extensão).

3. **Persistir modo selecionado** — salvar Suave/Equil/Agressivo no `SharedPreferences` e restaurar na próxima abertura.

### Prioridade baixa

4. **Indicadores no player YouTube** — injetar botões de speed/latência na barra do YouTube.

5. **Múltiplos canais** — permitir configurar mais de um canal e alternar entre eles.

6. **Notificação persistente** — mostrar status atual (modo, latência) numa notificação da barra de status.

7. **Suporte a landscape** — garantir que o overlay de modo funciona bem em rotação.

## Próximos passos

1. Testar em live real: verificar se o badge AO VIVO fica ativo e o delay reduz
2. Se a engine não encontrar `movie_player` (Shadow DOM), adicionar fallback com `querySelector`
3. Ajustar tick rate ou safety net baseado em testes reais
