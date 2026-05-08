# RokidGlassesAppCenter

Rokid Glasses にインストールされているアプリの **一覧・起動・停止・APK インストール・アンインストール** をスマホからリモート操作する管理ツール。

スマホ側 (`phone/`) からグラス側 (`glass/`) へ CXR-L SDK の `CUSTOMAPP` セッション + CustomCMD (JSON over Caps) で要求を送る。Client APK は phone のビルド時に `app/src/main/assets/client.apk` として自動同梱され、未インストールなら起動時にスマホからグラスへ push & install される。

## このリポジトリと依存リポジトリ

```
┌──────────────────────────────────────────┐
│ RokidGlassesAppCenter  ← このリポジトリ
│   phone/  : スマホ側アプリ (Compose)
│   glass/  : グラス側 APK (CustomCMD 受信側)
└────┬───────────────────────────┬─────────┘
     │ ① depends on              │ ② Caps シリアライザ / グラス側 SDK
     │ (Gradle composite build)  │ (Rokid maven)
     ▼                            ▼
   CxrGlobal              com.rokid.cxr:client-l (phone)
   (Hi Rokid global       com.rokid.cxr:cxr-service-bridge (glass)
    対応の薄いラッパー)
```

| 役割 | リポジトリ / 依存 | 説明 |
|---|---|---|
| ① ライブラリ | [TakanariShimbo/CxrGlobal](https://github.com/TakanariShimbo/CxrGlobal) | グローバル版 Hi Rokid 対応の CXR-L 薄いラッパー。`phone/` から Gradle composite build (`includeBuild("../../CxrGlobal")`) で取り込む |
| 本体 | **RokidGlassesAppCenter** (このリポ) | スマホ側 (`phone/`) + グラス側 (`glass/`) のセット。`phone/` から `glass/` を `includeBuild("../glass")` で composite build に取り込み、その debug APK を assets/client.apk に自動同梱 |
| ② Caps (phone) | `com.rokid.cxr:client-l:1.0.1` (Rokid maven) | Wire 互換のため本家 SDK の Caps シリアライザだけ借用 |
| ② Bridge (glass) | `com.rokid.cxr:cxr-service-bridge:1.0-20260212.103714-88` (Rokid maven) | グラス側の `CXRServiceBridge` 実装 |

## 主な機能

- グラスにインストールされているアプリの一覧取得
- アプリの起動 / 停止 (擬似 — Home に飛ばす)
- スマホから APK を選んでグラスにアップロード & インストール
- アンインストール (グラス側で確認ダイアログ)
- system アプリは一覧には出るがアンインストール不可
- Client APK 未インストール時の自動 push & start

## アーキテクチャ概要

```
[Host (phone)]                             [Client (glasses)]
HomeActivity
  └→ Hi Rokid 認可 → token
        │
AppListActivity ── CXRLink (CUSTOMAPP) ─→ CXRServiceBridge
        │                                          │
ClientBootstrap ── appUploadAndInstall / appStart ─┤
        │                                          ▼
AppMgrClient ── sendCustomCmd("appmgr.req", JSON) → AppManagerHandler
        ◀── sendMessage("appmgr.res", JSON) ───────
```

CustomCMD のワイヤフォーマット:

| 方向 | Caps key | payload |
|---|---|---|
| phone → glasses | `appmgr.req` | `{"id": uuid, "op": "list"\|"start"\|"stop"\|"uninstall", ...}` |
| glasses → phone | `appmgr.res` | `{"id": uuid, "ok": bool, "data": {...}` または `"error": "..."}` |

Op は `list` / `start` / `stop` / `uninstall` の 4 種、`id` で要求と応答を相関させる (`AppMgrClient`)。

## glass (Client) の設計上のポイント

- `MainActivity` は `android:launchMode="singleTask"` 必須。standard だと別アプリ起動時に Activity が破棄され、`CXRServiceBridge` の購読が消えて以降の要求が届かなくなる。
- `AppManagerHandler.start()` は `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` で対象アプリを起動する。`FLAG_ACTIVITY_NEW_TASK` 単体だと既存タスクの上に新インスタンスが積まれることがあり、それが Client 側の Activity を押し退けて bridge が落ちる原因になっていた。
- ワイヤプロトコル定数 (`Protocol.kt`) は Host 側の同名 object とバイト一致させること (Op 名・Caps キー)。
- `AppManagerHandler.stop` は本物の force-stop ではなく Home Intent を投げているだけ (非システムアプリは `killBackgroundProcesses` 等を持たないため)。

## 動作要件

| カテゴリ | 必要条件 | 動作確認済み |
|---|---|---|
| スマホ | Android (minSdk 31 / compileSdk 36) | Google Pixel 8 / Android 16 |
| グラス | ペアリング済 + Hi Rokid 認証通過 | Rokid Glasses / YodaOS SPRITE 1.18.007 |
| Hi Rokid アプリ | グローバル版 (`com.rokid.sprite.global.aiapp`) インストール済 | G1.5.9.0408 |

## セットアップ

### 1. 隣接配置で 2 リポジトリを clone

CxrGlobal は Gradle composite build (`includeBuild("../../CxrGlobal")`) で参照するので **同じ親ディレクトリに並べて** clone する:

```bash
cd ~/AndroidStudioProjects
git clone https://github.com/TakanariShimbo/CxrGlobal.git
git clone https://github.com/TakanariShimbo/RokidGlassesAppCenter.git
# → CxrGlobal / RokidGlassesAppCenter が並ぶ
```

### 2. SDK パスを設定

`phone/local.properties` と `glass/local.properties` のそれぞれに:

```properties
sdk.dir=/path/to/Android/Sdk
```

### 3. JDK は Android Studio バンドル JBR を使う

```bash
export JAVA_HOME=/opt/android-studio/jbr
export PATH=$JAVA_HOME/bin:$PATH
```

### 4. ビルド & 実機にインストール

phone 側だけでよい — `bundleClient` Gradle タスクが `glass/` のビルドを自動でトリガーし、生成された APK を `phone/app/src/main/assets/client.apk` にコピーする (手動 `adb push` は不要)。

```bash
cd RokidGlassesAppCenter/phone
./gradlew installDebug
```

## 使い方

1. アプリを起動 → Home 画面で Hi Rokid (グローバル版) のインストール状況を確認
2. 未インストールなら **Install Hi Rokid** でマーケットへ → 戻ったら **Recheck**
3. **Authorize** で Hi Rokid 認可フローを通し、token を取得
4. **Continue** で Glasses Apps 画面へ
5. グラス側で Client が未インストールなら自動でアップロード & 起動 (`Companion: ready` まで待つ)
6. アプリ一覧が表示される。各行で **Start / Stop / Uninstall**、上部の **Install APK** で APK を選んでアップロード可能

system アプリは badge 付きで表示され、Uninstall ボタンが無効化される。

## トラブルシューティング

- **Home 画面で「Hi Rokid: not installed」のまま**: グローバル版 (`com.rokid.sprite.global.aiapp`) が入っていない、または `phone/app/src/main/AndroidManifest.xml` の `<queries>` 漏れ
- **Authorize しても token が取れない**: Hi Rokid 側のサインイン状態を確認
- **AppList で `Companion: failed`**: グラスとの BT/CXR 接続失敗。`Link: connected` の表示も合わせて確認
- **アプリリストが取れない / Refresh がタイムアウトする**: グラス側 Client の Activity がスタックの裏に押しやられて bridge が停止している可能性。Client は `launchMode=singleTask`、Start ハンドラは `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` で対策済 — それでも再発するなら Client の挙動を確認
- **`installApk` で `APK copy failed`**: SAF で選択した URI が読めない。別の APK で試すか、ファイルマネージャでローカル DL ディレクトリに置いてから選ぶ
- **ビルド時に `Could not resolve com.example.cxrglobal:lib`**: CxrGlobal リポを並列に clone していない、または `phone/settings.gradle.kts` の `includeBuild` パスが合っていない
