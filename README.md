# Gcli2api Monitor

一款专为 [gcli2api](https://github.com/su-kaka/gcli2api) 打造的安卓客户端与桌面监控小组件（AppWidget）。

支持实时统计各账号池可用状态，将 **Gemini 系列** 与 **Claude 系列** 额度独立拆分核算，并支持桌面微件精准监控与一键交互刷新。

---

## ✨ 核心特性

- 📊 **总览与明细拆分**：
  - 自动汇总账号池可用账号数。
  - **Gemini 与 Claude 系列双额度独立核算**：精准计算各系列的平均剩余额度百分比及重置时间。
  - 支持查看各个凭据账号的具体剩余百分比与重置倒计时。
- 📱 **现代简约桌面组件（AppWidget）**：
  - 支持 **4x3 / 4x2** 响应式自适应网格，采用大圆角与 Catppuccin 深色现代质感主题。
  - 默认清晰展示至少 2 条账号详情卡片，多出账号支持**上下滑动浏览**。
  - 去除长邮箱后缀干扰，仅展示账号核心标识。
  - 支持右上角**一键点击即时刷新**，点击卡片直接唤起 App。
  - 支持 **5 分钟自动定时同步**（基于精准 Alarm 与后台 Worker）。
- 🔒 **隐私保护**：
  - 服务端配置与 API Key 均存储在本地设备私有存储中。
  - API Key 默认隐藏展示，支持密码明文/掩码一键切换。
- ⚡ **Xiaomi / HyperOS 友好适配**：
  - App 内部提供「添加组件」快捷入口（调用系统原生 `requestPinAppWidget`）。
  - 支持桌面长按 App 图标弹出「添加桌面组件」快捷菜单（App Shortcuts）。
  - 内置全密度高保真应用图标适配。

---

## 🛠️ 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose (Material 3)
- **桌面组件**：RemoteViews + RemoteViewsService (ListView 原生滑动)
- **网络与并发**：OkHttp + Kotlin Coroutines
- **后台同步**：AlarmManager + AndroidX WorkManager

---

## 🚀 快速上手

### 1. 下载与安装
从 [Releases](https://github.com/LanceHE6/gcli2api-monitor/releases) 页面下载最新的 `app-release.apk` 或 `app-debug.apk` 安装到手机。

### 2. 配置接入
1. 打开 App，输入你的 gcli2api 服务地址（如 `http://your-server-ip:7861`）。
2. 输入配置的 Bearer API Key。
3. 点击 **「保存并应用」**，数据将自动拉取呈现。

### 3. 添加桌面组件
- **方式一（App 内部）**：点击 App 顶栏右上角的 **「添加组件」**，在系统弹窗中确认添加（*若 HyperOS 未弹出，请前往应用权限设置开启「桌面快捷方式」权限*）。
- **方式二（长按图标）**：在桌面长按 App 图标，点击弹出的 **「添加桌面组件」** 快捷方式。
- **方式三（系统微件库）**：长按桌面空白处 ->「添加小部件」-> 滑到最底部「安卓小部件」中选择 **Gcli2api 状态组件** 拖至桌面。

---

## ⚙️ 编译与构建

本项目支持通过 Gradle 或 Docker 环境快速构建：

```bash
# 本地构建 Debug APK
./gradlew assembleDebug

# 构建产物位于
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 开源协议

本项目基于 [MIT](LICENSE) 协议开源。
