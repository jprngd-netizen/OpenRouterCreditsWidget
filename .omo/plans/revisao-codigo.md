# revisao-codigo - Work Plan

## TL;DR (For humans)

**What you'll get:** O código do widget vai ficar mais organizado (método gigante de 170 linhas quebrado em 3 classes focadas), mais rápido (bitmap de fundo cacheado em vez de recriado 3× por atualização), mais robusto (mostra último saldo conhecido quando estiver sem internet em vez de "…"), e com build portável (gradlew funcionando em clone fresco). No final, um APK novo é gerado e enviado no seu Telegram.

**Why this approach:** O maior problema do código hoje é o método `updateWidget()` que mistura fetch de API, processamento de dados e renderização de UI — qualquer mudança quebra tudo. Separar em camadas (dados → estado → render) deixa o código维护ável e prepara para features futuras sem risco de regressão. O cache offline e a correção de corrotina são bugs de UX disfarçados de features.

**What it will NOT do:** Não adiciona testes unitários, não troca a key pelo widget, não faz release assinado, não adiciona CI/CD, não migra para Compose.

**Effort:** Medium — 3 waves, 9 todos, ~7 arquivos modificados
**Risk:** Low — mudanças estruturais mas sem mudar API pública do widget nem endpoints
**Decisions I made for you:** PT-BR como idioma unificado (já é maioria no código), cache offline via SharedPreferences simples (dado semi-sensível, aceito), FLAG_IMMUTABLE sem fallback (minSdk 24 >= 23), Gson para serialização (já incluso), 3 classes extraídas (DataFetcher + StateManager + Renderer).

Your next move: approve, or run a high-accuracy review first. Full execution detail follows below.

---

> TL;DR (machine): Medium effort, low risk. Refactor god method into WidgetDataFetcher/WidgetRenderer/WidgetStateManager, fix coroutine leak, cache background bitmap, Gson serialization, PT-BR strings, generate Gradle wrapper, offline cache, build APK + Telegram delivery.

## Scope
### Must have
1. Extrair `updateWidget()` (~170 linhas) em **WidgetDataFetcher** (fetch + processamento), **WidgetRenderer** (RemoteViews), **WidgetStateManager** (cache offline + estados)
2. Corrigir leak de coroutine: `Job` controlado com cancelamento na reentrada
3. Cachear background drawable (evitar 3 renderizações por update)
4. `drawableToBitmap()` usar dimensões reais do widget, não 600x400 fixo
5. Substituir `JSONObject`/`JSONArray` manual por **Gson** no UsageStore
6. Mover strings hardcoded para `string.xml` em PT-BR
7. Gerar **Gradle wrapper completo** (gradlew, gradlew.bat, gradle-wrapper.jar)
8. Criar `proguard-rules.pro` real (não placeholder)
9. Extrair números mágicos como constantes nomeadas
10. Remover `Build.VERSION.SDK_INT >= M` dead code (minSdk=24)
11. Substituir `Triple<String, Double, String>` por `data class LastModelInfo`
12. Adicionar **cache offline**: último saldo conhecido em SharedPreferences
13. Build do APK + entrega via Telegram (usando Hermes se necessário)

### Must NOT have (guardrails, anti-slop, scope boundaries)
- ❌ Testes unitários (será outro plano)
- ❌ Features novas (troca de key no widget, release assinado, CI/CD)
- ❌ Migração para Compose / ViewBinding / DataBinding
- ❌ Analytics, telemetria, SDKs terceiros
- ❌ Modificar Theme.kt, ApiClient.kt, OpenRouterApi.kt (estão limpos)
- ❌ Mudar comportamento do WorkManager (15min periodic)
- ❌ Adicionar novos endpoints da OpenRouter
- ❌ Refatorar mais de 3 classes do provider (foco no god method apenas)

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- **Test decision:** none (out of scope per Must NOT have). Verification via build + manual smoke test.
- **Build verification:** `./gradlew assembleDebug` must succeed after every todo.
- **Smoke test:** After final todo, verify widget compiles, installs, and shows data.
- **Evidence:** `.omo/evidence/task-N-revisao-codigo.txt` (build output log per todo)

## Execution strategy
### Parallel execution waves

| Wave | Todos | Descrição |
|------|-------|-----------|
| **Wave 1: Build & Strings** | T1, T2 | Gerar wrapper, criar proguard-rules.pro, criar string.xml PT-BR. Sem interdependência entre si. |
| **Wave 2: Data Layer** | T3, T4, T5 | UsageStore→Gson, WidgetStateManager, WidgetDataFetcher. T4 e T5 dependem de T3 (UsageStore limpo). |
| **Wave 3: UI + Integração** | T6, T7, T8, T9 | WidgetRenderer, otimizações bitmap/constantes, refatorar Provider (usar todas as novas classes + Job control + constantes + Triple→data class + cache offline). Build + Telegram no final. |
| **Wave 4: Validação** | F1-F4 | Review final. |

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
|------|-----------|--------|---------------------|
| T1 (wrapper+proguard) | — | — | T2 |
| T2 (string resources) | — | — | T1 |
| T3 (UsageStore→Gson) | — | T4, T5 | T1, T2 |
| T4 (WidgetStateManager) | T3 | T8 | T5 |
| T5 (WidgetDataFetcher) | T3 | T8 | T4 |
| T6 (WidgetRenderer) | T2 | T8 | T4, T5 |
| T7 (bitmap cache + constants) | T6 | T8 | T4, T5 |
| T8 (refatorar Provider) | T4, T5, T6, T7 | T9 | — |
| T9 (build + Telegram) | T8 | — | — |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE - never rewrite the headers above. -->

- [x] 1. Gerar Gradle wrapper completo e criar proguard-rules.pro
  What to do / Must NOT do:
  - Rodar `cd /Users/gabriel.salem/android/OpenRouterCreditsWidget && ~/gradle89/gradle-8.9/bin/gradle wrapper --gradle-version 8.9` para gerar `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
  - Atualizar `.gitignore`: remover a exclusão de `gradlew`, `gradlew.bat` e `gradle-wrapper.jar` OU adicionar exceções (`!gradlew`, `!gradlew.bat`, `!gradle/wrapper/gradle-wrapper.jar`)
  - Criar `app/proguard-rules.pro` com regras mínimas:
    ```
    # Retrofit
    -keepattributes Signature
    -keepattributes *Annotation*
    -keep class retrofit2.** { *; }
    -keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
    # Gson
    -keep class com.gabrielsalem.openroutercredits.** { *; }
    -keepclassmembers class com.gabrielsalem.openroutercredits.** { *; }
    # OkHttp
    -dontwarn okhttp3.**
    -dontwarn okio.**
    # Coroutines
    -keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
    -keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
    ```
  - NÃO modificar nenhum código fonte neste todo
  Parallelization: Wave 1 | Blocked by: — | Blocks: —
  References (executor has NO interview context - be exhaustive):
  - AGENTS.md:54-59 (build commands)
  - build.gradle.kts:21 (referencia proguard-rules.pro existente)
  - .gitignore:1-16 (precisa de exceção para wrapper)
  - app/src/ (proguard-rules.pro não existe — criar em app/proguard-rules.pro)
  Acceptance criteria (agent-executable):
  - `ls -la gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar` → todos existem
  - `~/gradle89/gradle-8.9/bin/gradle wrapper --gradle-version 8.9` runs without error
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL (primeira vez usando wrapper)
  QA scenarios:
  - Happy: `./gradlew assembleDebug` → BUILD SUCCESSFUL in <5min
  - Failure: se gradle-wrapper.jar não for gerado, rodar manualmente `~/gradle89/gradle-8.9/bin/gradle wrapper --gradle-version 8.9`
  - Evidence: .omo/evidence/task-1-revisao-codigo.txt (build output)
  Commit: Y | `chore(build): gerar Gradle wrapper completo e criar proguard-rules.pro real`

- [x] 2. Criar string resources PT-BR e substituir strings hardcoded
  What to do / Must NOT do:
  - Criar `app/src/main/res/values/strings.xml` com os seguintes recursos:
    - `widget_title` = "OpenRouter"
    - `credits_loading` = "…"
    - `credits_no_key` = "sem chave"
    - `credits_no_key_hint` = "toque p/ configurar"
    - `credits_error` = "erro"
    - `last_model_prefix` = "último: %s · %s"
    - `today_prefix` = "hoje $%s"
    - `status_full` = "hoje $%.4f · 24h $%.4f · %s"
    - `status_medium` = "24h $%.4f · %s"
    - `config_title` = "OpenRouter API Key"
    - `config_hint` = "Cole a chave sk-or-... do painel da OpenRouter"
    - `config_key_empty_error` = "Informe a API key"
    - `config_save_btn` = "Salvar"
    - `config_theme_label` = "Esquema de cores"
    - `config_alpha_label` = "Transparência do fundo: %d%%"
    - `refresh_desc` = "Atualizar agora"
    - `spent_today` = "hoje $%.2f"
  - Em CreditsWidgetProvider.kt:
    - Substituir `"…"` por `context.getString(R.string.credits_loading)`
    - Substituir `"set key"` por `context.getString(R.string.credits_no_key)`
    - Substituir `"toque p/ configurar"` por `context.getString(R.string.credits_no_key_hint)`
    - Substituir `"erro"` por `context.getString(R.string.credits_error)`
    - Substituir `"último: $name · $spent"` por `String.format(context.getString(R.string.last_model_prefix), name, spent)`
    - Substituir strings de status por format com `String.format(context.getString(...), ...)`
    - Substituir `"hoje $%.2f"` por `String.format(context.getString(R.string.spent_today), spentToday)`
  - Em ConfigActivity.kt:
    - Substituir `"Transparência do fundo: $p%"` por `String.format(context.getString(R.string.config_alpha_label), p)`
    - Substituir `"Informe a API key"` por `context.getString(R.string.config_key_empty_error)`
  - NÃO mudar nomes de IDs de views, NÃO mudar lógica de negócio
  Parallelization: Wave 1 | Blocked by: — | Blocks: T6
  References:
  - CreditsWidgetProvider.kt:108-109, 128, 162-163, 178-180, 191-194, 199, 214-215 (strings hardcoded)
  - ConfigActivity.kt:60, 64, 72-73 (strings hardcoded)
  - widget_credits.xml:32, 41, 50, 73, 89, 97-99 (strings estao no layout, nao mexer — sao defaults que RemoteViews sobrescreve)
  - activity_config.xml:13, 20, 35, 47, 68 (idem — defaults)
  Acceptance criteria:
  - `grep -rn '"\$' app/src/main/java/ | grep -v 'import' | grep -v '//'` → verificar se restam strings de moeda hardcoded (ex: "$%.4f" dentro de string literal)
  - `grep -rn '"set key"\|"toque p/ configurar"\|"Informe a API key"\|Text = "hoje"\|text = "erro"' app/src/main/java/` → nenhum match
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  QA scenarios:
  - Happy: build passa, strings aparecem em PT-BR no widget
  - Failure: se alguma string ficou hardcoded, grep vai achar — corrigir
  - Evidence: .omo/evidence/task-2-revisao-codigo.txt (grep output + build log)
  Commit: Y | `refactor(i18n): migrar strings hardcoded para string.xml PT-BR`

- [x] 3. Substituir JSONObject/JSONArray manual por Gson no UsageStore
  What to do / Must NOT do:
  - Em UsageStore.kt:
    - Criar data class pública top-level (fora do object) `UsagePoint(val t: Long, val total: Double)` — Gson serializa sem adapter
    - Substituir `load()`: usar `Gson().fromJson(txt, Array<UsagePoint>::class.java)?.toList() ?: emptyList()`
    - Substituir `save()`: usar `Gson().toJson(points)` em vez de JSONArray manual
    - Trocar type de `List<Point>` para `List<UsagePoint>` (interno)
    - Adicionar `import com.google.gson.Gson`
    - No `catch` silencioso (linhas 73-75), adicionar log: `android.util.Log.w("UsageStore", "failed to persist", e)`
  - UsageStore private `data class Point` → remover (substituído por UsagePoint)
  - NÃO mudar API pública: `record()`, `series()`, `total24h()` devem manter assinaturas idênticas
  - NÃO adicionar dependências novas (Gson já está como transitive de converter-gson)
  Parallelization: Wave 2 | Blocked by: — | Blocks: T4, T5
  References:
  - UsageStore.kt:14-76 (todo o arquivo — serialização manual)
  - AGENTS.md:36 (Gson já incluso como converter-gson)
  - build.gradle.kts:42-44 (dependências existentes: retrofit2:converter-gson:2.11.0)
  Acceptance criteria:
  - `grep -rn 'JSONArray\|JSONObject' app/src/main/java/com/gabrielsalem/openroutercredits/UsageStore.kt` → zero matches (todo JSON removido)
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  QA scenarios:
  - Happy: build passa, UsageStore grava/carrega dados corretamente (verificar com logcat se necessario)
  - Failure: se Gson não serializar UsagePoint (data class com primitivos → sempre funciona), rever se precisa de @SerializedName
  - Evidence: .omo/evidence/task-3-revisao-codigo.txt (build log + grep output)
  Commit: Y | `refactor(store): substituir JSONObject manual por Gson no UsageStore`

- [x] 4. Criar WidgetStateManager com cache offline do último valor conhecido
  What to do / Must NOT do:
  - Criar `app/src/main/java/com/gabrielsalem/openroutercredits/WidgetStateManager.kt`:
    ```kotlin
    package com.gabrielsalem.openroutercredits

    import android.content.Context
    import android.content.SharedPreferences

    object WidgetStateManager {
        private const val PREFS_NAME = "or_widget_cache"
        private const val KEY_LAST_CREDITS = "last_credits"
        private const val KEY_LAST_UPDATE = "last_update_ms"
        private const val OFFLINE_THRESHOLD_MS = 30L * 60 * 1000 // 30 min

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun saveCredits(context: Context, remaining: Double) {
            prefs(context).edit()
                .putFloat(KEY_LAST_CREDITS, remaining.toFloat())
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()
        }

        fun getCachedCredits(context: Context): Double? {
            val prefs = prefs(context)
            if (!prefs.contains(KEY_LAST_CREDITS)) return null
            return prefs.getFloat(KEY_LAST_CREDITS, 0f).toDouble()
        }

        fun isOffline(context: Context): Boolean {
            val lastUpdate = prefs(context).getLong(KEY_LAST_UPDATE, 0L)
            return lastUpdate == 0L || (System.currentTimeMillis() - lastUpdate) > OFFLINE_THRESHOLD_MS
        }

        fun getDisplayCredits(context: Context): String? {
            val cached = getCachedCredits(context) ?: return null
            return "$%.4f".format(cached)
        }
    }
    ```
  - Nota: usar `SharedPreferences` simples (não Encrypted) porque o dado é semi-sensível (saldo, não API key). Risco aceito; se o usuário quiser encriptar depois, é fácil trocar.
  - NÃO incluir lógica de API, renderização, ou qualquer outra responsabilidade
  - NÃO usar EncryptedSharedPreferences (seria overhead desnecessário para cache de saldo)
  Parallelization: Wave 2 | Blocked by: T3 (UsageStore limpo) | Blocks: T8
  References:
  - AGENTS.md:33-35 (arquitetura existente — Prefs.kt usa EncryptedSharedPreferences para API key)
  - Prefs.kt (referência de padrão)
  - CreditsWidgetProvider.kt:92, 126, 208-215 (estados atuais: "…" loading, erro, sem cache offline)
  Acceptance criteria:
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  - `WidgetStateManager` compila (4 métodos públicos: saveCredits, getCachedCredits, isOffline, getDisplayCredits)
  QA scenarios:
  - Happy: salva Double via `saveCredits()`, recupera via `getCachedCredits()` → mesmo valor (float precision ok para 4 casas)
  - Failure: se `saveCredits` nunca foi chamado → `getCachedCredits` retorna null → `getDisplayCredits` retorna null
  - Evidence: .omo/evidence/task-4-revisao-codigo.txt (build log)
  Commit: Y | `feat(cache): adicionar WidgetStateManager com cache offline do saldo`

- [x] 5. Extrair WidgetDataFetcher com chamadas API + processamento
  What to do / Must NOT do:
  - Criar `app/src/main/java/com/gabrielsalem/openroutercredits/WidgetDataFetcher.kt`:
    ```kotlin
    package com.gabrielsalem.openroutercredits

    import android.content.Context

    data class WidgetData(
        val remainingCredits: Double,
        val creditsText: String,
        val spentToday: Double,
        val last7Days: List<Pair<String, Double>>,
        val topModels: List<Pair<String, Double>>,
        val lastModel: Triple<String, Double, String>?, // será substituído por LastModelInfo no T8
        val sparklineSeries: List<Pair<Long, Double>>,
        val total24h: Double,
        val activity: List<ActivityItem>
    )

    object WidgetDataFetcher {
        suspend fun fetch(context: Context, key: String): WidgetData {
            val credits = ApiClient.api.getCredits("Bearer $key")
            val activity = runCatching { ApiClient.api.getActivity("Bearer $key") }
                .getOrNull()?.data ?: emptyList()

            val remaining = credits.data.remaining_credits
                ?: (credits.data.total_credits - credits.data.total_usage)

            UsageStore.record(context, credits.data.total_usage)
            val series = UsageStore.series(context)
            val total24h = UsageStore.total24h(context)

            return WidgetData(
                remainingCredits = remaining,
                creditsText = "$%.4f".format(remaining),
                spentToday = if (activity.isNotEmpty()) ActivityStore.spentToday(activity) else 0.0,
                last7Days = ActivityStore.last7Days(activity),
                topModels = ActivityStore.topModels(activity, 3),
                lastModel = ActivityStore.lastModel(activity),
                sparklineSeries = series,
                total24h = total24h,
                activity = activity
            )
        }
    }
    ```
  - Mover as chamadas de API e processamento de dados DO COMPANION OBJECT de CreditsWidgetProvider para este novo arquivo
  - Remover as linhas correspondentes de CreditsWidgetProvider.kt (linhas 119-127, 138-141, 150-151, 160-162, 176-177)
  - NÃO mover lógica de RemoteViews, renderização, ou PendingIntents
  - NÃO incluir lógica de tema ou layout tier
  Parallelization: Wave 2 | Blocked by: T3 (UsageStore limpo) | Blocks: T8
  References:
  - CreditsWidgetProvider.kt:119-203 (bloco try do fetch — todo o fetch + processamento)
  - CreditsWidgetProvider.kt:95-117 (validação de key — NÃO mover, é do provider)
  - UsageStore.kt, ActivityStore.kt (dados que o fetcher consome)
  - OpenRouterApi.kt, ApiClient.kt (API calls — já existem, fetcher só chama)
  Acceptance criteria:
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  - `WidgetDataFetcher.fetch()` compila e retorna `WidgetData`
  - CreditsWidgetProvider.kt não contém mais `ApiClient.api.getCredits` nem `ApiClient.api.getActivity`
  QA scenarios:
  - Happy: build passa, fetch() retorna WidgetData com todos os campos preenchidos
  - Failure: se API falha, `runCatching` retorna lista vazia para activity — widget data ainda tem remainingCredits
  - Evidence: .omo/evidence/task-5-revisao-codigo.txt (grep + build log)
  Commit: Y | `refactor(data): extrair WidgetDataFetcher com fetch + processamento`

- [x] 6. Extrair WidgetRenderer para construção de RemoteViews
  What to do / Must NOT do:
  - Criar `app/src/main/java/com/gabrielsalem/openroutercredits/WidgetRenderer.kt`:
    ```kotlin
    package com.gabrielsalem.openroutercredits

    import android.appwidget.AppWidgetManager
    import android.content.Context
    import android.content.Intent
    import android.graphics.Bitmap
    import android.graphics.Canvas
    import android.graphics.Color
    import android.os.Build
    import android.view.View
    import android.widget.RemoteViews
    import androidx.core.content.ContextCompat
    import java.text.SimpleDateFormat
    import java.util.Date
    import java.util.Locale

    //... todos os métodos de renderização movidos do CreditsWidgetProvider

    // layoutTier enum + fun
    // renderLoading, renderNoKey, renderContent, renderError
    // drawableToBitmap, refreshIntent
    // backgroundDrawable NÃO — já existe em Theme.kt
    ```
  - Mover para WidgetRenderer:
    - `enum Tier` (privado ou internal)
    - `fun layoutTier(opts)` — os thresholds agora usam constantes (definir em WidgetConstants no T7 ou manter local)
    - `fun renderLoading(context, theme, bgAlpha): RemoteViews`
    - `fun renderNoKey(context, theme, bgAlpha, appWidgetId): RemoteViews`
    - `fun renderContent(context, data: WidgetData, tier, theme, bgAlpha): RemoteViews`
    - `fun renderError(context, theme, bgAlpha, message): RemoteViews`
    - `fun drawableToBitmap(drawable, width, height): Bitmap` — agora aceita width/height
    - `fun refreshIntent(context, appWidgetId): PendingIntent`
    - `fun configIntent(context, appWidgetId): PendingIntent`
  - Todos os `setTextColor`, `setImageViewBitmap`, `setViewVisibility` vão para este renderer
  - `backgroundDrawable()` JÁ existe em Theme.kt — o renderer só chama
  - A formatação de strings agora usa `context.getString(R.string.xxx)` dos resources
  - NÃO incluir lógica de API fetch ou data processing
  - NÃO incluir WidgetStateManager (cache offline)
  - NÃO mudar cores, layout, comportamento do widget
  Parallelization: Wave 3 | Blocked by: T2 (string resources existem) | Blocks: T7, T8
  References:
  - CreditsWidgetProvider.kt:65-75 (Tier enum + layoutTier)
  - CreditsWidgetProvider.kt:82-117 (renderLoading, renderNoKey)
  - CreditsWidgetProvider.kt:130-206 (renderContent)
  - CreditsWidgetProvider.kt:208-222 (renderError)
  - CreditsWidgetProvider.kt:226-246 (drawableToBitmap, refreshIntent, configIntent)
  - Theme.kt:48-57 (backgroundDrawable — não mover, só chamar)
  - widget_credits.xml (layout IDs: bg, credits, title, updated, sparkline, bars7d, top_models, last_model, spent_today, root, refresh)
  Acceptance criteria:
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  - CreditsWidgetProvider.kt não contém mais `RemoteViews(`, `setImageViewBitmap`, `setTextViewText`, `setTextColor`, `setViewVisibility`
  - WidgetRenderer contém todos os métodos de renderização
  QA scenarios:
  - Happy: build passa, 4 métodos de render existem (loading, noKey, content, error)
  - Failure: se algum `setOnClickPendingIntent` ou `R.id.xxx` ficou no provider → build falha ou warning de unused import
  - Evidence: .omo/evidence/task-6-revisao-codigo.txt (grep + build log)
  Commit: Y | `refactor(ui): extrair WidgetRenderer para construção de RemoteViews`

- [x] 7. Otimizações: cache de background bitmap, constantes nomeadas, dimensões reais
  What to do / Must NOT do:
  - **Cache de background (usar LruCache obrigatoriamente — não usar Map simples):**
    - Em WidgetRenderer ou WidgetStateManager, implementar cache com `LruCache`:
      ```kotlin
      private val backgroundCache = object : LruCache<String, Bitmap>(50) {
          override fun sizeOf(key: String, bitmap: Bitmap): Int {
              return bitmap.allocationByteCount / 1024 // KB
          }
      }
      
      fun getCachedBackground(theme: WidgetTheme, bgAlpha: Int, width: Int, height: Int): Bitmap {
          val key = "${theme.id}_${bgAlpha}_${width}_${height}"
          return backgroundCache.get(key) ?: run {
              val bmp = drawableToBitmap(backgroundDrawable(theme, bgAlpha), width, height)
              backgroundCache.put(key, bmp)
              bmp
          }
      }
      ```
    - IMPORTANTE: usar `android.util.LruCache` (import `android.util.LruCache`), com maxSize=50 (cobre ~7 temas × vários alphas sem estourar memória). `NÃO` usar `mutableMapOf` (cresce sem limite)
    - Chamar `getCachedBackground()` em vez de `drawableToBitmap(backgroundDrawable(...))` em loading, content, error states
    - Apenas 1 bitmap de fundo por (theme, alpha, width, height) — não 3

  - **Dimensões reais do widget:**
    - Modificar `drawableToBitmap()` para aceitar `width` e `height` como parâmetros
    - Obter dimensões reais do widget via `AppWidgetManager.getAppWidgetOptions()` → `OPTION_APPWIDGET_MIN_WIDTH` e `OPTION_APPWIDGET_MIN_HEIGHT` convertidos para px
    - Substituir o argumento do bitmap de fundo para usar as dimensões reais

  - **Extrair constantes nomeadas:**
    - Criar `object WidgetConstants` em CreditsWidgetProvider.kt (ou arquivo separado WidgetConstants.kt):
      ```kotlin
      object WidgetConstants {
          const val TIER_FULL_MIN_WIDTH = 250
          const val TIER_FULL_MIN_HEIGHT = 180
          const val TIER_MEDIUM_MIN_WIDTH = 200
          const val TIER_MEDIUM_MIN_HEIGHT = 140
          const val BITMAP_DEFAULT_WIDTH = 600
          const val BITMAP_DEFAULT_HEIGHT = 400
          const val BG_CORNER_RADIUS_DP = 24f
          const val BG_BITMAP_QUALITY = Bitmap.Config.ARGB_8888
          const val SPARKLINE_PADDING_TOP = 0.12f
          const val SPARKLINE_PADDING_BOTTOM = 0.12f
          const val BARS_PADDING_BOTTOM = 0.18f
          const val BARS_GAP_RATIO = 0.04f
          const val OFFLINE_THRESHOLD_MINUTES = 30
      }
      ```

  - **Remover Build.VERSION.SDK_INT >= M dead code:**
    - Em `refreshIntent()` e `configPendingIntent()`: remover o `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)` — minSdk=24, FLAG_IMMUTABLE sempre disponível
    - Simplificar: `val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`
    - Remover `import android.os.Build` se não for mais usado em outros lugares

  - NÃO modificar cores, layout, comportamento do widget
  - NÃO quebrar o cache (se não couber em memória, pode estourar — com 7 temas × 100 alphas = 700 max, cada ~200KB ≈ 140MB, o que é alto. Usar `LruCache` ou limitar a N entradas)
  
  Parallelization: Wave 3 | Blocked by: T6 (WidgetRenderer existe) | Blocks: T8
  References:
  - CreditsWidgetProvider.kt:65-73 (thresholds layout — magic numbers)
  - CreditsWidgetProvider.kt:227-235 (drawableToBitmap — 600x400 fixo)
  - CreditsWidgetProvider.kt:242-244 (Build.VERSION.SDK_INT >= M — dead code)
  - WidgetCharts.kt:85-87 (cor "#999999" hardcoded — não mexer agora, só se for constante)
  - Theme.kt:53 (cornerRadius 24f)
  - UsageStore.kt:17 (WINDOW_MS)
  Acceptance criteria:
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  - `WidgetConstants.TIER_FULL_MIN_WIDTH` == 250 (constante extraída)
  - `grep -rn 'SDK_INT.*VERSION_CODES.M' app/src/main/java/` → nenhum match
  - `drawableToBitmap` aceita width/height como parâmetros
  - `grep -rn 'LruCache' app/src/main/java/com/gabrielsalem/openroutercredits/WidgetRenderer.kt | head -1` → match (LruCache usado, NÃO mutableMapOf)
  - `grep -rn 'mutableMapOf.*Bitmap' app/src/main/java/com/gabrielsalem/openroutercredits/` → nenhum match (sem cache sem limite)
  QA scenarios:
  - Happy: build passa, background é cacheado (somente 1 bitmap por theme/alpha/dimensão)
  - Failure: se LruCache não implementado, o Map pode crescer infinitamente. Se for Map simples sem limite, substituir por LruCache(MAX_ENTRIES = 50)
  - Evidence: .omo/evidence/task-7-revisao-codigo.txt (grep + build log)
  Commit: Y | `perf: cachear background bitmap, extrair constantes, remover dead code SDK`

- [x] 8. Refatorar CreditsWidgetProvider: integrar novas classes + Job control + cache offline + Triple→LastModelInfo
  What to do / Must NOT do:
  - **Criar data class LastModelInfo** em ActivityStore.kt (ou novo arquivo Models.kt):
    ```kotlin
    data class LastModelInfo(
        val model: String,
        val spent: Double,
        val date: String
    )
    ```
  - **ActivityStore.lastModel()**: mudar retorno de `Triple<String, Double, String>?` para `LastModelInfo?`
    - Atualizar implementação: `return LastModelInfo(model, spent, latestDate)`
    - Adicionar `import` se em arquivo separado

  - **WidgetData**: substituir campo `lastModel: Triple<...>?` por `lastModel: LastModelInfo?`

  - **Refatorar updateWidget() em CreditsWidgetProvider:**
    ```kotlin
    companion object {
        private var fetchJob: Job? = null
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            // 1. Cancelar fetch anterior
            fetchJob?.cancel()
            
            // 2. Obter tema, alpha, tier
            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val tier = WidgetRenderer.layoutTier(opts) // movido para WidgetRenderer
            val theme = WidgetTheme.fromId(Prefs.getTheme(context, appWidgetId))
            val bgAlpha = Prefs.getBgAlpha(context, appWidgetId)
            
            // 3. Verificar key
            val key = Prefs.getKey(context, appWidgetId)
            if (key.isNullOrBlank()) {
                val rv = WidgetRenderer.renderNoKey(context, theme, bgAlpha, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, rv)
                return
            }
            
            // 4. Mostrar loading com cache offline se disponivel
            val cachedCredits = WidgetStateManager.getDisplayCredits(context)
            val loadingRv = WidgetRenderer.renderLoading(context, theme, bgAlpha, cachedCredits)
            appWidgetManager.updateAppWidget(appWidgetId, loadingRv)
            
            // 5. Fetch em background com Job control
            fetchJob = ioScope.launch {
                try {
                    val data = WidgetDataFetcher.fetch(context, key)
                    
                    // Salvar no cache offline
                    WidgetStateManager.saveCredits(context, data.remainingCredits)
                    
                    // Renderizar conteudo (precisa voltar pra Main thread)
                    withContext(Dispatchers.Main) {
                        val rv = WidgetRenderer.renderContent(context, data, tier, theme, bgAlpha, appWidgetId)
                        rv.setOnClickPendingIntent(R.id.refresh, WidgetRenderer.refreshIntent(context, appWidgetId))
                        appWidgetManager.updateAppWidget(appWidgetId, rv)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val rv = WidgetRenderer.renderError(context, theme, bgAlpha, e.message ?: "erro", appWidgetId)
                        rv.setOnClickPendingIntent(R.id.refresh, WidgetRenderer.refreshIntent(context, appWidgetId))
                        appWidgetManager.updateAppWidget(appWidgetId, rv)
                    }
                }
            }
        }
        
        // cancelUpdates() — opcional, chamado se necessario
    }
    ```

  - **ATENÇÃO:**
    - `layoutTier()` agora está em WidgetRenderer. Chamar `WidgetRenderer.layoutTier(opts)` em vez do método removido
    - `refreshIntent()` agora está em WidgetRenderer. Chamar `WidgetRenderer.refreshIntent()` 
    - ConfigActivity.pendingIntent() já está em ConfigActivity — manter
    - `R.id.root` onClick: `ConfigActivity.pendingIntent()` — manter no renderContent
    - `R.id.refresh` onClick precisa ser adicionado depois do render — fazer no provider
    - Import: `kotlinx.coroutines.*` (Job, CoroutineScope, SupervisorJob, Dispatchers, launch, withContext)

  - **Remover imports não usados** após a refatoração (optimize imports)

  - NÃO quebrar `onReceive(ACTION_REFRESH)` — manter intent filter e refresh flow
  - NÃO quebrar `onEnabled` / `onDisabled` — manter schedule/cancel do WorkManager **E ADICIONAR** `ioScope.cancel()` em `onDisabled()`
  - NÃO quebrar `onAppWidgetOptionsChanged` — mantém updateWidget
  - **IMPORTANTE: Coroutine scope lifecycle** — o `ioScope` é criado no companion object e vive para sempre. `onDisabled()` DEVE chamar `ioScope.cancel()` para limpar o escopo quando o último widget for removido. Se necessário, recriar o scope se `onEnabled()` for chamado novamente depois.

  Parallelization: Wave 3 | Blocked by: T4 (WidgetStateManager), T5 (WidgetDataFetcher), T6 (WidgetRenderer), T7 (constantes) | Blocks: T9
  References (executor has NO interview context - be exhaustive):
  - CreditsWidgetProvider.kt:1-248 (arquivo INTEIRO — vai ser drasticamente reduzido)
  - ActivityStore.kt:52-61 (lastModel retorna Triple → mudar para LastModelInfo)
  - WidgetRenderer.kt (novo — layoutTier, refreshIntent, render* todos lá)
  - WidgetDataFetcher.kt (novo — fetch())
  - WidgetStateManager.kt (novo — saveCredits, getDisplayCredits)
  - WidgetConstants (novo ou objeto no provider)
  - AGENTS.md:33-35 (Conta-wide: /credits e /activity são da CONTA)
  Acceptance criteria:
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  - CreditsWidgetProvider.kt tem no máximo ~80 linhas (reduzido de 248)
  - `grep -rn 'Triple' app/src/main/java/com/gabrielsalem/openroutercredits/` → sem matches (Triple removido)
  - `grep -rn 'fetchJob' app/src/main/java/com/gabrielsalem/openroutercredits/CreditsWidgetProvider.kt` → existe (Job control implementado)
  - `grep -n 'onDisabled' app/src/main/java/com/gabrielsalem/openroutercredits/CreditsWidgetProvider.kt` → contém `ioScope.cancel()`
  QA scenarios:
  - Happy: build passa, widget funciona (fetch → render), offline cache salva
  - Failure: se fetch lança exceção → renderiza erro com mensagem; se offline → mostra cachedCredits
  - Evidence: .omo/evidence/task-8-revisao-codigo.txt (build log + line count)
  Commit: Y | `refactor(widget): integrar classes extraídas, Job control, cache offline, LastModelInfo`

- [ ] 9. Build do APK + entrega via Telegram
  What to do / Must NOT do:
  - Rodar `cd /Users/gabriel.salem/android/OpenRouterCreditsWidget && ./gradlew assembleDebug --no-daemon`
  - Verificar APK em `app/build/outputs/apk/debug/app-debug.apk`
  - Enviar APK via Telegram:
    - Usar curl para Telegram Bot API:
      ```bash
      curl -F chat_id=<CHAT_ID> -F document=@app/build/outputs/apk/debug/app-debug.apk \
        -F caption="OR Credits Widget v1.6 — revisao de codigo + cache offline" \
        https://api.telegram.org/bot<TOKEN>/sendDocument
      ```
    - `<TOKEN>` e `<CHAT_ID>`: obter via Hermes (o usuário disse "usar o hermes para fazer o envio. ele tem as credenciais")
    - Se Hermes tiver um script, usá-lo diretamente: `hermes send-telegram-document --file app/build/outputs/apk/debug/app-debug.apk --caption "OR Credits Widget v1.6"`
  - NÃO assinar o APK (debug build é suficiente para testes)
  - NÃO fazer release build (fora do escopo)
  - **Hermes availability check:**
    1. Executar `which hermes || test -f ~/bin/hermes || test -f /opt/homebrew/bin/hermes` para localizar Hermes
    2. Se Hermes existir, usar comando: `hermes send-telegram-document --file "app/build/outputs/apk/debug/app-debug.apk" --caption "OR Credits Widget v1.6 — revisao de codigo"`
    3. Se Hermes não existir, verificar variáveis de ambiente: `test -n "$TELEGRAM_BOT_TOKEN" && test -n "$TELEGRAM_CHAT_ID"` → usar curl como fallback
  Parallelization: Wave 3 | Blocked by: T8 (código compilável) | Blocks: —
  References:
  - AGENTS.md:54-59 (comandos de build completos)
  - AGENTS.md:1-4 (v1.5 atual — próxima versão será v1.6)
  - ~/Library/LaunchAgents/ ou ~/bin/ (possível local do Hermes — verificar se existe `which hermes` ou `~/bin/hermes`)
  Acceptance criteria:
  - `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL
  - `ls -la app/build/outputs/apk/debug/app-debug.apk` → existe e > 0 bytes
  - `which hermes || test -f ~/bin/hermes || test -f /opt/homebrew/bin/hermes` → pelo menos um OK, OU `test -n "$TELEGRAM_BOT_TOKEN" && test -n "$TELEGRAM_CHAT_ID"` → ambos setados
  - Telegram message com APK enviada com sucesso (HTTP 200 do Bot API ou saida 0 do Hermes)
  QA scenarios:
  - Happy: build passa, APK gerado, curl retorna `{"ok":true}`, usuário recebe APK no Telegram
  - Failure: se Telegram falha (token inválido, chat ID errado), mostrar erro mas não impedir entrega — usuário pode pegar APK localmente
  - Failure: se build falha, NÃO tentar enviar APK — reportar erro e parar
  - Evidence: .omo/evidence/task-9-revisao-codigo.txt (build log + curl response)
  Commit: Y | `chore(release): build APK v1.6 e entregar via Telegram`

## Final verification wave
> Runs in parallel after ALL todos. ALL must APPROVE. Surface results and wait for the user's explicit okay before declaring complete.
- [ ] F1. **Plan compliance audit**: todos os 9 todos foram executados conforme especificado? Nenhum passo foi pulado ou alterado sem justificativa? Verificar commits vs plano.
- [ ] F2. **Code quality review**: o código compilou sem warnings? As importações estão limpas? `./gradlew assembleDebug` sem erros?
- [ ] F3. **Real manual QA**: o APK foi instalado e o widget mostra dados corretamente? (verificar via logcat que `WidgetDataFetcher.fetch()` retorna dados)
- [ ] F4. **Scope fidelity**: nenhuma feature nova foi adicionada além do especificado? Cache offline é a única adição? Nenhum build.gradle.kts ou dependência foi alterada além do necessário?

## Commit strategy
Commits atômicos e compiláveis, um por todo:

| Todo | Mensagem |
|------|----------|
| T1 | `chore(build): gerar Gradle wrapper completo e criar proguard-rules.pro real` |
| T2 | `refactor(i18n): migrar strings hardcoded para string.xml PT-BR` |
| T3 | `refactor(store): substituir JSONObject manual por Gson no UsageStore` |
| T4 | `feat(cache): adicionar WidgetStateManager com cache offline do saldo` |
| T5 | `refactor(data): extrair WidgetDataFetcher com fetch + processamento` |
| T6 | `refactor(ui): extrair WidgetRenderer para construção de RemoteViews` |
| T7 | `perf: cachear background bitmap, extrair constantes, remover dead code SDK` |
| T8 | `refactor(widget): integrar classes extraídas, Job control, cache offline, LastModelInfo` |
| T9 | `chore(release): build APK v1.6 e entregar via Telegram` |

Ao final, 9 commits em sequência. Se houver necessidade de squash, manter a granularidade — cada commit é independente e compilável.

## Success criteria
1. ✅ `./gradlew assembleDebug` compila sem erros
2. ✅ `updateWidget()` reduzido de ~170 linhas para ~50 linhas (delega para WidgetDataFetcher + WidgetRenderer + WidgetStateManager)
3. ✅ `grep -rn 'JSONArray\|JSONObject' app/src/main/java/com/gabrielsalem/openroutercredits/` → zero matches
4. ✅ `grep -rn '"set key"\|"toque p/ configurar"\|"Informe a API key"' app/src/main/java/` → zero matches (todas em string.xml)
5. ✅ `grep -rn 'SDK_INT.*VERSION_CODES.M' app/src/main/java/` → zero matches (dead code removido)
6. ✅ `grep -rn 'Triple' app/src/main/java/com/gabrielsalem/openroutercredits/` → zero matches (substituído por LastModelInfo)
7. ✅ Widget offline mostra último saldo conhecido em vez de "…"
8. ✅ WidgetStateManager.getDisplayCredits() retorna null quando nunca houve dado, ou o último valor quando offline
9. ✅ Coroutine usa Job control com cancelamento na reentrada (fetchJob?.cancel())
10. ✅ Gradle wrapper funcional: `./gradlew assembleDebug` funciona em clone fresco
11. ✅ APK enviado via Telegram com sucesso
