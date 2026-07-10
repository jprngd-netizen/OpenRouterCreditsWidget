# OpenRouter Credits Widget — AGENTS.md

## Situação Atual (2026-07-10)
App Widget Android (Kotlin, sem Compose) que mostra saldo de créditos da OpenRouter + uso recente em tempo real. v1.3 publicada no GitHub, APK enviado via Telegram.

## Trabalho Concluído (100%)
- ✅ Projeto Android criado e buildado (Gradle 8.9 manual; SDK instalado via Homebrew + cmdline-tools)
- ✅ Widget funcional: saldo (`/credits`), gasto hoje + barras 7d (`/activity`), sparkline 15min (derivação local)
- ✅ Layout responsivo por tamanho (tiers COMPACT/MEDIUM/FULL via `onAppWidgetOptionsChanged`)
- ✅ Segurança: `EncryptedSharedPreferences` (Keystore AES-256-GCM), `allowBackup=false`, campo `textPassword`
- ✅ Versionado no GitHub (público): `jprngd-netizen/OpenRouterCreditsWidget`, branch `main`
- ✅ README.md (EN) + LICENSE (MIT) + disclaimer de projeto pessoal
- ✅ Handoff: `docs/HANDOFF.md` (wiki) + `docs/router.md`

## Versões
- v1.1: crédito + hardening de segurança
- v1.2: sparkline 24h (derivação local)
- v1.3: `/activity` (hoje + 7d + top modelos), layout responsivo, java.time desugaring (commit `e3901b1`)
- Docs: commit `6185e51`

## APKs (Telegram, chat admin)
- v1.1 → msg 775 | v1.2 → msg 776 | v1.3 → msg 777

## Estrutura
```
app/src/main/java/com/gabrielsalem/openroutercredits/
  OpenRouterApi.kt ApiClient.kt Prefs.kt CreditsWidgetProvider.kt
  ConfigActivity.kt WidgetUpdateScheduler.kt UsageStore.kt
  ActivityStore.kt WidgetCharts.kt
docs/HANDOFF.md  docs/router.md  README.md  LICENSE
```

## Pontos Críticos
- **CONTA-WIDE**: `/credits` e `/activity` são da CONTA, não da key. Qualquer `sk-or-*` válida devolve o mesmo total. Não há filtro por key na API.
- **Granularidade**: `/activity` é DIÁRIA. Resolução sub-dia (15min) vem de delta local (`UsageStore`), não da API.
- **Pendência**: `gradle-wrapper.jar` NÃO commitado. Clone fresco precisa de `gradle` direto ou `gradle wrapper --gradle-version 8.9`.

## Endpoints (verificados na spec OpenAPI 3.1.0)
- `GET /api/v1/credits` → `{data:{total_credits,total_usage}}`
- `GET /api/v1/activity` → `{data:[ActivityItem]}` (date, model, usage, byok_usage_inference, requests, tokens...)

## Próximos Passos (opcionais)
1. Commitar `gradle-wrapper.jar` pra clone funcionar com `./gradlew`
2. Release assinado (tag v1.3 + APK no GitHub Releases)
3. Cache offline do último valor (não mostrar "…" sem rede)
4. Botão de troca de key direto no widget

## Credenciais / Ambiente
- Repo: `https://github.com/jprngd-netizen/OpenRouterCreditsWidget` (owner `jprngd-netizen`)
- API key: NÃO versionada (vive em `EncryptedSharedPreferences` no device)
- Build local: JDK17 `/opt/homebrew/opt/openjdk@17`, Android cmdline-tools `/opt/homebrew/share/android-commandlinetools`, Gradle 8.9 em `~/gradle89`

## Comandos
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
cd ~/android/OpenRouterCreditsWidget
~/gradle89/gradle-8.9/bin/gradle assembleDebug --no-daemon
```
