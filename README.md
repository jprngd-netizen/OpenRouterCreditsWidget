# OpenRouter Credits Widget (Android)

App Widget que mostra os créditos restantes da OpenRouter em tempo real.

## Como funciona
- Na primeira vez que você adiciona o widget, ele pede a API key (`sk-or-...`).
- Faz `GET https://openrouter.ai/api/v1/credits` e exibe `remaining_credits`.
- Atualiza a cada 15 min via WorkManager + botão de refresh manual no widget.
- Toque no corpo do widget para reconfigurar a chave.

## Build
Abra a pasta `OpenRouterCreditsWidget` no Android Studio (Arquivo > Open).
Conecte um telefone (USB/ADB) ou use um emulador e rode `app`.

Para gerar o APK de release:
```
./gradlew assembleRelease
```
(O wrapper é gerado automaticamente ao abrir no Android Studio; se preferir linha de comando, rode `gradle wrapper` antes.)

## Notas
- `updatePeriodMillis` no XML é 30 min (mínimo do Android); o refresh real é via WorkManager a cada 15 min + botão manual.
- A chave fica salva em `SharedPreferences` do app (por widget id). Não vai pra lugar nenhum além da OpenRouter.
- minSdk 24 (Android 7.0+), targetSdk 34.

## Estrutura
```
app/src/main/
  AndroidManifest.xml
  java/com/gabrielsalem/openroutercredits/
    CreditsWidgetProvider.kt   # receiver + atualização
    ConfigActivity.kt          # tela de configuração da key
    OpenRouterApi.kt           # modelo + interface Retrofit
    ApiClient.kt               # instância Retrofit
    Prefs.kt                   # SharedPreferences
    WidgetUpdateScheduler.kt   # WorkManager periódico
  res/layout/widget_credits.xml
  res/layout/activity_config.xml
  res/xml/credits_widget_info.xml
  res/drawable/widget_bg.xml
```
