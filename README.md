# YtCar — 透過 Android Auto 在車機上播放 YouTube（自用）

在手機上選片，車機只負責全螢幕播放。手機端使用已登入的 YouTube，所以訂閱、播放清單、Premium 免廣告都能用。

## 安裝

1. 到 [Releases](../../releases/latest) 下載最新的 `YtCar-*.apk`，在手機上安裝。
2. **開啟 Android Auto 開發者模式**：手機設定 → Android Auto → 往下捲，連續點「版本」10 次 → 確定。
3. 右上角「⋮」→ 開發人員設定 → 勾選 **「未知來源」**。
4. 開啟 YtCar → 點右上角頭像登入 Google 帳號（只需要登入一次）。
5. 手機連上車子 → 車機的 App 列表會出現 **YtCar**（它是以導航 App 的身分出現）→ 開啟。

## 使用

- 在手機的 YtCar 裡點任何一部影片 → 影片會送到車機播放，手機不會播。
- 也可以在**官方 YouTube App** 按「分享」→ 選 YtCar，直接送到車機播放。
- 方向盤／車機的媒體鍵（上一首、下一首、播放暫停）會控制 YtCar。
- 遙控列：🏠 首頁、⏪10 / 10⏩ 倒轉或快轉 10 秒、⏯ 播放或暫停、⏭ 播佇列中的下一部、☰ 佇列。
- 車機正在播放時選新影片，可以選擇「立即播放」或「加入佇列」，播完會自動接著播下一部。
- **乘客模式**：預設在車速超過 7 km/h 時，車機會遮住畫面、只播聲音；打開乘客模式後，行車中也會顯示影片。
  如果車機沒有提供車速資料，遙控列會顯示「車速無法取得」，這時不會自動切換成只播聲音。

> ⚠️ 依台灣法規，駕駛人在行車中不得觀看影像。乘客模式是給乘客使用的。

## 技術概要

- 以 Car App Library 的導航類 App 取得車機的 Surface → `VirtualDisplay` → `Presentation` → WebView（m.youtube.com）。
- 手機端和車機端在同一個 process 裡，共用 WebView 的 Cookie（登入狀態）以及 `PlaybackController` 的狀態。
- 每次 push 到 `main`，GitHub Actions 就會自動建置並發布 Release。版本號 = `0.1.<run number>`。
- 簽章金鑰放在 GitHub Secrets（`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`），不放在 repo 裡。
- Releases 只保留最新一版，舊版本和舊 tag 會自動刪除。
