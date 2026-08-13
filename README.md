<p align="center">
  <img src="./tv/src/main/res/drawable/aulama_tv_logo.png" width="360" alt="Aulama TV" />
</p>

<h1 align="center">Aulama TV</h1>

<p align="center">
  為 Android TV、Google TV 及 Android 手機而設嘅 Aulama IPTV 播放器。
</p>

<p align="center">
  <a href="https://github.com/siumiu1968/Aulama_TV/releases/tag/v2.6.16-family"><img src="https://img.shields.io/github/v/release/siumiu1968/Aulama_TV?display_name=tag&label=Android%20TV&color=2383e2" alt="Android TV release" /></a>
  <a href="https://github.com/siumiu1968/Aulama_TV/releases"><img src="https://img.shields.io/github/downloads/siumiu1968/Aulama_TV/total?label=downloads" alt="Downloads" /></a>
  <a href="https://apilevels.com/"><img src="https://img.shields.io/badge/Android-5.0%2B-3ddc84?logo=android" alt="Android 5.0+" /></a>
  <a href="./LICENSE"><img src="https://img.shields.io/github/license/siumiu1968/Aulama_TV" alt="MIT License" /></a>
</p>

> 目前正式版本：Android TV `2.6.16-family`、Android 手機 `1.1.3`。

## 新版介面

### 頻道與節目指南

<img src="./screenshots/aulama-tv-channel-guide.png" width="100%" alt="Aulama TV 頻道及節目指南" />

頻道清單、目前／下一節節目、播放資訊及節目指南會保持同一個遙控器操作流程；節目資料按香港時間顯示。

### 智能播放線路

<img src="./screenshots/aulama-tv-smart-routes.png" width="100%" alt="Aulama TV 智能播放線路面板" />

用遙控器即可切換自動、香港、日本或直接連線模式，亦可為個別線路設定手動優先次序。

## 功能

- **遙控器優先介面**：適配 Android TV、Google TV 及 D-pad；清晰顯示焦點、目前狀態及可操作項目。
- **節目單**：顯示目前、下一節與當日節目；支援 XMLTV／XMLTV.GZ 自訂來源。
- **智能多線路**：按畫質、成功率、啟動速度、卡頓與近期穩定觀看表現排列候選線路；播放異常會先自動重試，再平順切換後備。
- **手動優先**：可為同一頻道排定多條優先線路；自動模式會在首選失敗後繼續後備。
- **Aulama ID**：訪客可直接使用；登入後可用 QR Code／配對碼同步收藏、自訂 M3U 及線路優先次序。
- **播放相容性**：按裝置能力選擇合適的解碼及色彩路徑，舊 Android TV 不支援 HDR 時會避開不合適線路。
- **個人化**：支援收藏、數字選台、換台反轉、自訂 M3U／TVBox 清單與自訂節目單。

## 下載

| 平台 | 正式版本 | 下載 |
| --- | --- | --- |
| Android TV／Google TV | `2.6.16-family` | [下載 APK](https://github.com/siumiu1968/Aulama_TV/releases/tag/v2.6.16-family) |
| Android 手機 | `1.1.3` | [下載 APK](https://github.com/siumiu1968/Aulama_TV/releases/tag/android-v1.1.3) |
| 網頁版 | 最新版 | [開啟 Aulama IPTV](https://aulama.org/iptv/) |

## 基本操作

- `上／下`：切換頻道或移動焦點。
- `OK`：確認目前選項。
- `Menu／選單鍵`：開啟播放控制中心，再進入節目指南、切換線路、畫面比例或設定。
- `左／右`：在支援嘅播放畫面快速調整線路；所有設定均可由遙控器完整操作。
- `數字鍵`：直接選台。

## 自訂內容

App 支援匯入自己擁有或獲授權嘅 M3U／TVBox 直播源，以及 XMLTV／XMLTV.GZ 節目單。Aulama ID 為自選功能，未登入仍可播放及管理本機內容。

## 開發

需求：Android SDK、JDK 17、Android 5.0（API 21）或以上裝置。

```bash
./gradlew :tv:assembleRelease
./gradlew :mobile:assembleRelease
```

主要模組：

- `tv/`：Android TV／Google TV 體驗。
- `mobile/`：Android 手機體驗。
- `core/`：播放、清單、節目單及同步共用邏輯。

## 使用聲明

本項目只提供播放器與清單管理功能，不擁有、託管或保證任何第三方直播內容。請只匯入你有權使用嘅來源，並遵守所在地法律及內容供應商條款。

## 致謝

- [my-tv](https://github.com/lizongying/my-tv)
- [fanmingming/live](https://github.com/fanmingming/live)
