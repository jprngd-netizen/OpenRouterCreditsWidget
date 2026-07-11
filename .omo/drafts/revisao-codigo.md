---
slug: revisao-codigo
status: awaiting-approval
intent: unclear
pending-action: write .omo/plans/revisao-codigo.md
approach: Refatoracao estrutural do widget: extrair responsabilidades do metodo god (updateWidget), corrigir leak de corrotina, otimizar renderizacao de bitmap, substituir JSON manual por Gson, unificar idioma das strings, eliminar numeros magicos, corrigir build (gradle-wrapper.jar + proguard), e adicionar cache offline do ultimo valor conhecido.
---

# Draft: revisao-codigo

## Components (topology ledger)
| id | Component | Outcome (one line) | Status | Evidence |
|----|-----------|-------------------|--------|----------|
| C1 | ARQUITETURA | updateWidget() (~170 linhas) extraido em WidgetDataFetcher + WidgetRenderer + WidgetStateManager | active | CreditsWidgetProvider.kt:77-224 |
| C2 | COROUTINAS | CoroutineScope(Dispatchers.IO).launch sem lifecycle vira escopo controlado com cancelamento | active | CreditsWidgetProvider.kt:119 |
| C3 | PERFORMANCE | backgroundDrawable() renderizado 3x por update vira cache de bitmap; drawableToBitmap() usa tamanho real do widget | active | CreditsWidgetProvider.kt:88-93, 131, 210, 227-235 |
| C4 | INTERNACIONALIZACAO | Strings misturadas PT/EN ("set key", "toque p/ configurar", "erro", "Informe a API key") viram recursos string.xml em PT-BR | active | CreditsWidgetProvider.kt:108-109, ConfigActivity.kt:72 |
| C5 | CONSTANTES | Numeros magicos (tiers 250/180/200/140, tamanho bitmap 600x400) viram constantes nomeadas | active | CreditsWidgetProvider.kt:65-73, 227-235 |
| C6 | BUILD | gradle-wrapper.jar ausente, proguard-rules.pro referenciado mas inexistente, versoes soltas | active | build.gradle.kts:21, .gitignore, gradle/wrapper/ |
| C7 | SERIALIZACAO | UsageStore usa JSONObject/JSONArray manual; Gson ja e dependencia | active | UsageStore.kt:50-76 |
| C8 | RESILIENCIA | Widget mostra "…" ou "erro" sem rede; deveria mostrar ultimo valor conhecido + indicador offline | active | CreditsWidgetProvider.kt:92, 214-215 |

## Open assumptions (announced defaults)
| Assumption | Adopted default | Rationale | Reversible? |
|------------|----------------|-----------|-------------|
| Qual revisao fazer | Revisao estrutural + qualidade + build + resiliencia (nao features novas) | "Revise o codigo" sem especificacao → melhor pratica e limpeza/manutencao | Sim: usuario pode pedir feature especifica |
| Idioma das strings | PT-BR (consistente com strings existentes "toque p/ configurar", "hoje", "erro", "ultimo:") | Codigo ja usa PT-BR nas strings de UI, exceto "set key" que foge | Sim: facil trocar para EN |
| Escopo de refatoracao | Extrair 3 classes do updateWidget(), nao refatorar tudo | Metodo god de ~170 linhas e o maior problema estrutural | Sim: pode expandir ou reduzir |
| Cache offline | SharedPreferences simples com ultimo valor + timestamp | Nao requer DB, minima complexidade, mantem widget funcional sem rede | Sim: pode virar Room futuramente |
| Serialization | Substituir JSONObject manual por Gson (ja incluido) | Gson ja e dependencia, zero custo adicional, codigo mais limpo | Sim: reverte facil |
| gradle-wrapper.jar | Commitar (adicionar no .gitignore a excecao) | Sem ele ./gradlew nao funciona; melhor pratica Android | Sim: pode remover depois |
| Nao fazer testes unitarios | Fora do escopo desta revisao | "Revisao" e estrutural, nao adicao de coverage; testes seriam outro plano | — |

## Findings (cited - path:lines)

### CRITICAL - God method em CreditsWidgetProvider.kt
- `updateWidget()` (linhas 77-224): ~170 linhas, 5 responsabilidades misturadas:
  - Layout tier selection + theming (linhas 82-100)
  - Loading state rendering (linhas 87-93)
  - API fetch + data processing (linhas 119-127)
  - Content rendering + chart generation (linhas 130-206)
  - Error state rendering (linhas 208-222)
- `drawableToBitmap()` (linhas 227-235): tamanho fixo 600x400, ignora dimensoes reais do widget
- `layoutTier()` (linhas 65-73): thresholds hardcoded (250, 180, 200, 140)
- `refreshIntent()` (linhas 242-244): `Build.VERSION.SDK_INT >= M` e dead code (minSdk=24)
- Chama `backgroundDrawable()` 3x por update (loading, content, error)

### HIGH - Coroutine leak
- Linha 119: `CoroutineScope(Dispatchers.IO).launch` sem lifecycle. Se o widget for atualizado rapido, multiplas coroutines acumulam. Nunca sao canceladas.

### MEDIUM - Manual JSON parsing
- UsageStore.kt linhas 50-76: `JSONArray`/`JSONObject` manual quando Gson ja esta em `implementation("com.squareup.retrofit2:converter-gson:2.11.0")`
- Silent catch (linhas 73-75) engole excecoes de I/O

### MEDIUM - Build issues
- `gradle-wrapper.jar` nao versionado → `./gradlew` quebra em clone fresco
- `build.gradle.kts:21` referencia `proguard-rules.pro` que nao existe
- Versoes de dependencias (Retrofit 2.11.0, OkHttp 4.12.0, etc.) sao strings soltas sem catalog

### LOW - Mixed language strings
- CreditsWidgetProvider.kt:108: `"set key"` (EN) vs linha 109: `"toque p/ configurar"` (PT)
- CreditsWidgetProvider.kt:180: `"último:"` (PT), linha 192-194: `"hoje"` (PT), linha 199: `"hoje"` (PT)
- CreditsWidgetProvider.kt:214: `"erro"` (PT)
- ConfigActivity.kt:72: `"Informe a API key"` (PT) vs labels em EN no layout

### LOW - Numeros magicos
- CreditsWidgetProvider.kt:65-73: thresholds de layout (250, 180, 200, 140) sem nomes
- CreditsWidgetProvider.kt:227, 229: tamanho bitmap (600, 400) fixo
- WidgetCharts.kt:85-87: cor `"#999999"` hardcoded

### LOW - Triple<String, Double, String> em ActivityStore
- ActivityStore.kt:52: `lastModel()` retorna `Triple` → ilegivel, deveria ser data class `LastModelInfo`

## Decisions (with rationale)

1. **Scope: structural + quality only, no new features.** "Revise o codigo" = revisao, nao adicao. Cache offline e a unica excecao porque substitui o comportamento "…"/"erro" que e um bug de UX, nao feature.
2. **PT-BR como idioma unificado.** O codigo ja usa PT-BR majoritariamente. Unificar evita confusao. Reversivel.
3. **Extrair 3 classes do god method** em vez de refatorar todo o provider. Foco no maior problema estrutural.
4. **Gson para serializacao.** Ja incluso, sem custo, codigo mais limpo. Reversivel.
5. **CoroutineScope com Job control** em vez de lifecycle-aware scope (widgets Android nao tem lifecycle tradicional). Um `var job: Job?` permite cancelar o anterior antes de lancar novo.

## Scope IN
1. Extrair WidgetDataFetcher (API fetch + data processing) de CreditsWidgetProvider
2. Extrair WidgetRenderer (construcao RemoteViews + charts) de CreditsWidgetProvider
3. Extrair WidgetStateManager (cache offline, loading/error/content states) de CreditsWidgetProvider
4. Substituir CoroutineScope(...).launch por Job control + cancelamento
5. Cachear backgroundDrawable() renderizado para evitar 3x por update
6. Usar tamanho real do widget em drawableToBitmap() em vez de 600x400 fixo
7. Mover strings para resources (string.xml em PT-BR)
8. Substituir JSONObject manual por Gson em UsageStore
9. Extrair numeros magicos como constantes nomeadas
10. Commitar gradle-wrapper.jar
11. Criar proguard-rules.pro legitimo
12. Adicionar cache offline do ultimo valor conhecido (SharedPreferences)

## Scope OUT (Must NOT have)
- Nao adicionar testes unitarios (sera outro plano)
- Nao adicionar features novas (troca de key no widget, release assinado, CI/CD)
- Nao migrar para Compose ou ViewBinding
- Nao adicionar analytics, telemetria, ou SDKs terceiros
- Nao modificar a API do OpenRouter nem adicionar endpoints
- Nao refatorar Theme.kt, ApiClient.kt, ou OpenRouterApi.kt (estao limpos)
- Nao mudar o comportamento do WorkManager (15min periodic)

## Metis findings incorporated
- **Boundaries claras**: WidgetDataFetcher = so dados/API, WidgetRenderer = so RemoteViews, WidgetStateManager = so estado/cache. Sem overlap.
- **Coroutine pattern**: Usar `var fetchJob: Job?` com cancelamento na reentrada. Nao usar scope persistente (widget provider nao tem lifecycle).
- **Bitmap threading**: ja esta em `Dispatchers.IO` (CoroutineScope). Manter consistente.
- **Cache offline**: SharedPreferences simples (nao Encrypted) — risco aceito para dado semi-sensivel (saldo, nao key). Se o usuario quiser encryptar, e facil.
- **Gson Point**: `UsagePoint` como data class publica top-level. Gson serializa sem adapter.
- **Wrapper**: gerar gradlew + gradlew.bat + gradle-wrapper.jar (tudo faltando, nao so o jar).

## Open questions
Nenhuma — todas resolvidas por pesquisa + defaults.

## Approval gate
status: awaiting-approval
pending-action: write .omo/plans/revisao-codigo.md
approach: Refatoracao estrutural do widget: extrair responsabilidades do metodo god (updateWidget), corrigir leak de corrotina, otimizar renderizacao de bitmap, substituir JSON manual por Gson, unificar idioma das strings, eliminar numeros magicos, corrigir build (gradle-wrapper.jar + proguard), e adicionar cache offline do ultimo valor conhecido.
