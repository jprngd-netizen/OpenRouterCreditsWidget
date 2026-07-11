# Learnings - revisao-codigo

## 2026-07-11 Início da execução

- Projeto: OpenRouterCreditsWidget (Kotlin, Android Widget, sem Compose)
- Build: Gradle 8.9 manual em ~/gradle89, JDK17, Android cmdline-tools
- NÃO existe gradlew / gradle-wrapper.jar (precisa ser gerado)
- NÃO existe proguard-rules.pro
- NÃO existe strings.xml (strings hardcoded no código)
- minSdk = 24 (dead code `SDK_INT >= M` existe)
- Gson já incluso via converter-gson (transitivo)

- [x] Gerar Gradle wrapper completo e criar proguard-rules.pro real

## 2026-07-11 T3 complete: UsageStore migrado de JSONObject para Gson
