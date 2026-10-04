# FlowKeyboard · 传送带旋转输入法

<p align="center">
  <img src="app/src/main/res/drawable/ic_flow_keyboard.xml" width="96" height="96" alt="FlowKeyboard Logo" />
</p>

<p align="center">
  <strong>一款基于 Jetpack Compose 与物理模拟的旋转传送带 Android 输入法</strong><br>
  <em>Inspired by Google Japan Gboard Conveyor Belt Version ("Gboard くるくるバージョン")</em>
</p>

<p align="center">
  <a href="https://github.com/Qinwusui/flow-keyboard/actions/workflows/ci.yml">
    <img src="https://github.com/Qinwusui/flow-keyboard/actions/workflows/ci.yml/badge.svg" alt="CI Status" />
  </a>
  <img src="https://img.shields.io/badge/Android-SDK%2026--36-3DDC84?logo=android&logoColor=white" alt="Android SDK" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Room-2.8.5-009688" alt="Room" />
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" />
  </a>
</p>

---

## 📖 简介 / Introduction

**FlowKeyboard (传送带旋转输入法)** 颠覆了传统静态九宫格和全键盘的打字逻辑。按键分布在连续循环流动的传送带轨道上：
- **单指巡航瞄准输入**：只需将手指悬停在固定目标准星线上，按键流经准星时轻触即可极速键入；
- **全域点按与惯性滑送**：支持直接点击屏幕任意传送带位置，或通过手势拖拽、惯性甩动（Fling）自由调节轨道流速；
- **纯本地离线与隐私优先**：无需且未申请网络权限，所有词库匹配与自学习模型均在本地设备运行。

---

## ✨ 核心特性 / Features

### 🔄 传送带多轨道交互 (Conveyor Belt Mechanics)
- **四轨布局 (4 Conveyor Lanes)**：
  - **Track 0**：数字与常用符号
  - **Track 1**：字母 Q - P
  - **Track 2**：字母 A - M
  - **Track 3**：系统功能键（Space / Backspace / Enter / Shift / 模式切换）
- **双向布局呈现**：横向流动传送带 (Horizontal Conveyor) 与 纵向瀑布流 (Vertical Waterfall) 一键切换。
- **键盘排列支持**：经典 QWERTY 键盘顺序与 A-Z 字母自然顺序。
- **物理运动学模拟**：精确的连续环形周期坐标映射、目标击中判别以及阻尼衰减惯性物理算法。

### 🇨🇳 智能拼音与二元语法预测 (Intelligent Pinyin & Bigram Prediction)
- **60,000+ 超大离线词库**：预置高频词组与成语库，极速本地 SQLite 前缀检索。
- **拼音首字母匹配 (Initial Consonants)**：支持首字母简拼（如键入 `nh` 智能联想 `你好`）。
- **二元接续词推荐 (Bigram Transitions)**：基于上下文历史词汇自动预测接续候选词。
- **动态频率自学习 (Self-Learning Rank Inversion)**：用户频繁选中的词汇权重自动升级，越用越顺手。
- **全屏候选词展开面板 (Expanded Candidate Panel)**：支持水平 CandidateBar 滚动与折叠/展开多行快速翻页选词。

### 🎨 Material 3 主题系统 (Material You Theming)
- **动态取色**：原生适配 Android Material You `dynamicLightColorScheme` / `dynamicDarkColorScheme`。
- **内置特色主题**：预设经典流光、暮光暖橘、深海幽蓝、翡翠森林、赛博霓虹与梦幻薰衣草多种配色。

### 🔊 机械触感与拟物音效 (Audio & Haptics)
- **机械微动音效**：通过 SoundPool 呈现机械键盘按键敲击音与传送带转动滴答声。
- **阶梯震动反馈**：针对轻触、长按与甩动提供不同层级的触觉震感。

### 🔒 隐私与安全性 (Privacy First)
- **零网络权限**：完全不申请 `android.permission.INTERNET`，彻底杜绝数据外泄风险。
- **敏感输入保护**：进入密码框或隐身模式时，自动停用词库学习与输入统计。
- **本地统计**：仅在本地记录 WPM 打字速度与按键次数分析。

---

## 🛠 技术架构 / Tech Stack

项目采用现代 Android 架构设计规范（Now in Android / Clean Architecture）：

- **UI 框架**：Jetpack Compose BOM、Material 3、Compose Canvas 自定义渲染
- **服务载体**：`FlowKeyboardService` (`InputMethodService`)，拥有独立生命周期与状态管理
- **本地数据库**：Room 2.8.5（支持 v1 至 v7 完整数据库迁移及架构导出验证）
- **偏好持久化**：Jetpack DataStore Preferences
- **依赖注入**：Koin 4.2.2
- **编译器与优化**：Kotlin 2.2.10、KSP 2.2.10-2.0.2、R8 Full Mode 全量混淆优化

---

## 🚀 编译与运行 / Build & Run

### 环境要求
- **JDK**：17+
- **Android SDK**：API 26 (Min) ~ API 36 (Compile/Target)
- **Gradle**：9.2.1 (内置 Gradlew)

### 常用构建命令

```bash
# 运行单元测试（涵盖 100+ 物理算法、拼音引擎、Room 迁移与自学习测试）
./gradlew testDebugUnitTest --info

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（执行 R8 深度代码优化与资源压缩）
./gradlew assembleRelease
```

编译生成的 APK 位于：
`app/build/outputs/apk/debug/app-debug.apk`

### 启用输入法指南
1. 安装生成的 APK 并打开 FlowKeyboard 主应用；
2. 在应用内可通过内置的 **打字沙盒 (Sandbox)** 直接体验输入与设置；
3. 点击 **启用键盘 (Enable FlowKeyboard)**，在系统设置中开启 FlowKeyboard；
4. 点击 **选择键盘 (Select Keyboard)**，切换为 FlowKeyboard 即可在任意应用中使用。

---

## 📂 目录结构 / Project Structure

```text
flow-keyboard/
├── app/
│   ├── schemas/                          # Room 数据库架构导出版本 (v1 ~ v7)
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/                   # 60k+ 词典 (dictionary.tsv) 与接续词表 (transitions.tsv)
│   │   │   ├── java/com/flowkeyboard/android/
│   │   │   │   ├── data/                 # Room 实体、DAO 与 DataStore 数据源
│   │   │   │   ├── di/                   # Koin 依赖注入模块定义
│   │   │   │   ├── engine/               # 物理模拟、拼音分词、按键动作分发与音效触感引擎
│   │   │   │   ├── model/                # 轨道定义、按键项与输入法设置模型
│   │   │   │   ├── ui/                   # Jetpack Compose 界面 (键盘、传送带画布、控制栏、候选面板)
│   │   │   │   └── FlowKeyboardApp.kt    # Application 入口
│   │   │   └── res/                      # 矢量图标、多语言字符串 (values/values-zh) 与音效
│   │   └── test/                         # 完整的单元测试套件
├── gradle/                               # Gradle Wrapper 与 Version Catalog (libs.versions.toml)
├── tools/                                # 离线词库生成与声效合成辅助脚本
├── .github/workflows/ci.yml              # GitHub Actions 自动化构建与测试工作流
├── LICENSE                               # Apache 2.0 开源协议
└── README.md
```

---

## 📄 开源许可与致谢 / License & Acknowledgments

本项目遵循 [Apache License 2.0](LICENSE) 协议开源。

- **词库数据源**：词汇频率数据源自 [wordfreq](https://github.com/rspeer/wordfreq)（Apache 2.0 协议）并采用 CC BY-SA 4.0 整理。
- **拼音映射**：拼音注音处理辅助工具参考 [pypinyin](https://github.com/mozillazg/python-pinyin)（MIT 协议）。
- 详细第三方许可文件请参阅 [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/)。
