# OpenRouter Credits Widget

A simple Android home-screen widget that shows your [OpenRouter](https://openrouter.ai) credit balance in real time, plus recent usage.

![widget tiers](https://img.shields.io/badge/widget-compact%20%7C%20medium%20%7C%20full-blue)

## Features

- **Live credit balance** — fetches `GET /api/v1/credits` and displays remaining credits (`total_credits − total_usage`).
- **Today's spend** — sums `usage` for the current day from `GET /api/v1/activity`.
- **7-day usage bars** — one bar per day (the granularity OpenRouter actually returns), drawn natively in the widget.
- **15-minute real-time sparkline** — because `/activity` is daily-only, the widget also derives a sub-day spend curve from the delta between consecutive polls (stored locally).
- **Top models (24–48h)** — the 3 most expensive models in the last two days.
- **Last model used** — shows the model with the highest spend on the most recent day with activity, plus that day's spend (e.g. `last: gpt-4.1 · $0.015`). Note: `/activity` is daily-only, so this is a proxy for "last model", not the exact last call.
- **Themes + transparency** — pick from 7 predefined color schemes (Dark, Light, AMOLED, Ocean, Sunset, Mint, Grape) and a 0–100% background transparency slider, all configured per-widget in the config screen.
- **Responsive layout** — the amount of detail adapts to the widget size:
  - **Compact (≈2×2):** balance + last update time only.
  - **Medium (≈3×3):** + 15-min sparkline + "24h" total + last model.
  - **Full (≈4×4+):** + 7-day bars + top models + last model + "today / 24h / time" line.
- **Manual refresh** — tap the sync button for an on-demand update (system minimum update interval is 15 min for the periodic one).
- **Per-widget API key** — each widget stores its own OpenRouter key in `EncryptedSharedPreferences` (Android Keystore, AES-256-GCM). The key never leaves the device except in the HTTPS request to OpenRouter.

## Security notes

- The API key is stored encrypted at rest (`androidx.security:security-crypto`) and is masked while typing.
- `android:allowBackup="false"` so the key is **not** included in Android/Google backups.
- All traffic goes over HTTPS to `openrouter.ai` only. There is no analytics, telemetry, or third-party SDK.
- The key has the same power as your account's API key: a leak lets someone spend your credits (it does **not** expose your account password or billing card). If OpenRouter offers a read-scoped key, prefer it for the widget.

## Data the app reads

| Endpoint | Used for |
|----------|----------|
| `GET /api/v1/credits` | `total_credits`, `total_usage` → remaining balance |
| `GET /api/v1/activity` | daily `usage` per model → today's spend, 7-day bars, top models |

## Build

Requirements: Android SDK (compileSdk 34), JDK 17, Gradle 8.9.

```bash
git clone https://github.com/jprngd-netizen/OpenRouterCreditsWidget.git
cd OpenRouterCreditsWidget
./gradlew assembleDebug
```

The unsigned debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. For a release build, create a signing key and configure `signingConfigs` in `app/build.gradle.kts`.

## Install

Sideload the debug APK or build a release and install it. Then:

1. Long-press the home screen → *Widgets* → drag **OR Credits** onto the home screen.
2. Paste your OpenRouter API key (`sk-or-...`) in the config screen.
3. Resize the widget to change how much detail is shown.

## Architecture

```
app/src/main/java/com/gabrielsalem/openroutercredits/
├── OpenRouterApi.kt        # Retrofit interface + data models
├── ApiClient.kt            # singleton Retrofit instance
├── Prefs.kt                # EncryptedSharedPreferences per widget id
├── CreditsWidgetProvider.kt# AppWidgetProvider: fetch, layout tiers, refresh
├── ConfigActivity.kt       # key entry screen
├── WidgetUpdateScheduler.kt# WorkManager 15-min periodic update
├── UsageStore.kt           # local 24h spend series (for the sparkline)
├── ActivityStore.kt        # derive today/7d/top-models/last-model from /activity
├── Theme.kt                # 7 predefined color schemes + dynamic background drawable
└── WidgetCharts.kt         # sparkline + bar chart bitmaps (theme-aware colors)
```

## ⚠️ Personal project — no maintenance guarantee

This is a **personal project** built for my own use. It is provided as-is, without any guarantee of continued development, timely updates, or support. The OpenRouter API may change at any time and break the widget; I may or may not fix it. Use at your own risk.

## License

MIT — see [LICENSE](LICENSE).
