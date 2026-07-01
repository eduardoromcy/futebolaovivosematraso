# BACKLOG

## Implementado

### WebView + Engine (PlayerActivity.kt)

- [x] WebView com `youtube.com/watch?v=...` — desktop UA (Windows Chrome 125)
- [x] Engine JS injetada com ciclo **burst**: `init → rest → accel → rest → accel → ...`
- [x] `setPlaybackRate()` via `document.getElementById('movie_player')` (funciona com SABR/manifestless)
- [x] Leitura de `getStatsForNerds()` (live_latency_secs, buffer_health_seconds)
- [x] Leitura de `getProgressState()` (isAtLiveHead, seekableEnd, current)
- [x] **3 modos de velocidade**: Suave (1.1x), Equilibrado (1.25x), Agressivo (1.5x)
- [x] Seletor de modo no overlay superior (botões)
- [x] Banner de status com fase, velocidade, latência e buffer
- [x] Ciclo fixo: accel → rest (~5s) → accel → ...
- [x] **Auto-adjust timings**: `getSegDuration()` lê `segduration`/`latencyClass` do player e escala duração dos ciclos
- [x] **Skip threshold safety net**: se delay > 30s durante accel, seek ao vivo + rest
- [x] Seek ao vivo no `init` e a cada 6 ticks no `rest` (mantém badge AO VIVO ativo)
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

## Comparação com live-catch-up extension

Repo de referência: [yudai-tiny-developer/live-catch-up](https://github.com/yudai-tiny-developer/live-catch-up)

| Funcionalidade | Nós | Extensão |
|---|---|---|
| **Estratégia de catch-up** | Burst cycle fixo (accel → rest → accel) | Adaptativo por buffer (speed se buffer > threshold) |
| **Playback rate API** | `player.setPlaybackRate()` ✅ (funciona SABR) | `video.playbackRate` ❌ (ignorado no YouTube moderno) |
| **Auto-adjust timings** | ✅ `getSegDuration()` por `latencyClass` | ✅ `calc_threathold()` por `segduration` |
| **Skip threshold** | ✅ 30s (agressivo) | ✅ 300s (default, configurável) |
| **Stats** | `getStatsForNerds()` | `getStatsForNerds()` |
| **Progress** | `getProgressState()` | `getProgressState()` |
| **Respeitar controle manual** | ❌ (deliberado) | ✅ só acelera se speed atual = 1.0 |
| **Indicadores visuais** | Banner no topo (fase, speed, latência, buffer) | Botões na barra do YouTube (speed, latency, health, estimativa, elapsed) |
| **Estimativa de chegada** | ❌ | ✅ mostra horário estimado |
| **Modos** | 3 fixos (1.1x / 1.25x / 1.5x) | 1 configurável (1.05x–16x) |
| **Tick rate** | 500ms | 250ms |
| **Fallback seek** | Apenas safety net (30s) | Configurável (default 300s) |
| **Timestamp copy** | ❌ | ✅ click copia URL com timestamp |
| **Persistence** | ❌ (reinicia modo padrão) | ✅ chrome.storage |

## Sugestões futuras

### Prioridade alta

1. **Indicador de estimativa no banner** — durante accel, mostrar "chegando ao vivo em ~X segundos" (igual a extensão faz). Basta calcular `(seekableEnd - currentTime) / (speed - 1.0)`.

2. **Fallback seek adicional** — ter um segundo threshold tipo 120s além do safety net de 30s, como rede de segurança extra para casos extremos.

3. **Persistir modo selecionado** — salvar Suave/Equil/Agressivo no `SharedPreferences` e restaurar na próxima abertura.

### Prioridade média

4. **Tick rate 250ms** — reduzir o `JS_INTERVAL_MS` e o `engineRunnable.postDelayed` para resposta mais rápida. Cuidado com overhead de `evaluateJavascript`.

5. **Respeitar controle manual (toggle)** — adicionar opção que, se ativada, só acelera se o usuário não mexeu na velocidade manualmente (similar à extensão).

6. **Indicadores opcionais no player** — injetar botões na barra do YouTube (playback rate, latência) com toggle pra ligar/desligar.

### Prioridade baixa

7. **Múltiplos canais** — permitir configurar mais de um canal e alternar entre eles.

8. **Notificação persistente** — mostrar status atual (modo, latência) numa notificação da barra de status.

9. **Modo escuro** — tema escuro na tela principal (hoje já é escuro por default, mas formalizar).

10. **Suporte a landscape** — garantir que o overlay de modo funciona bem em rotação.

## Próximos passos imediatos

1. Testar o burst cycle em live real (verificar se badge AO VIVO fica ativo e delay reduz visivelmente)
2. Se a engine não encontrar `movie_player` (Shadow DOM), adicionar fallback
3. Ajustar timings do ciclo baseado em testes reais
