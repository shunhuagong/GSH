# GSH — 增肌饮食记录

面向增肌人群的三餐饮食记录 App：记录吃了什么，自动换算成当天蛋白质 / 碳水 / 脂肪摄入，并与增肌目标对比。纯本地存储，无需联网。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）
- Room 数据库（本地持久化）
- Gradle 8.11.1 + AGP 8.7.3，compileSdk 35 / minSdk 26

## 功能

| 页面 | 说明 |
|---|---|
| 今日 | 按日期查看三大营养素 + 热量摄入进度条，按早餐/午餐/晚餐/加餐分组显示记录，可前后翻页切换日期 |
| 添加记录 | 选餐别 → 搜索食物 → 输入克数 → 实时预览营养 → 保存 |
| 食物库 | 预置约 45 种常见食物，肉类区分（生）（熟）口径（按称重状态选择，避免失水导致的高估/低估），支持搜索、添加自定义食物（可选 g/ml/个 单位）、删除自定义食物 |
| 历史 | 按天汇总的摄入列表，点击任意一天回看当天的详细记录 |
| 设置 | 体重 + 增肌系数（蛋白/碳水/脂肪 g/kg），自动计算每日营养目标 |

默认增肌目标：体重 × 蛋白 2.0g/kg、碳水 4.0g/kg、脂肪 0.8g/kg，可在设置页调整。

## 构建

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 用 Android Studio 打开

File → Open → 选择本目录（含 `settings.gradle.kts`），等待 Gradle Sync 完成后点 Run ▶️ 即可安装到模拟器或真机。

## 目录结构

```
app/src/main/java/com/gsh/app/
├── MainActivity.kt          # 入口 Activity
├── data/                    # 数据层
│   ├── Food.kt              # 食物实体 + 营养换算
│   ├── MealEntry.kt         # 饮食记录实体（营养快照）
│   ├── FoodDao.kt           # 食物 DAO
│   ├── MealEntryDao.kt      # 记录 DAO + 每日汇总 DAO
│   ├── AppDatabase.kt       # Room 数据库
│   ├── SeedFoods.kt         # 预置食物库
│   ├── SettingsStore.kt     # 用户设置（SharedPreferences）
│   ├── GshRepository.kt    # 数据仓库
│   └── DateUtils.kt         # 日期工具
└── ui/                      # Compose UI
    ├── Theme.kt             # 主题 + 营养素配色
    ├── GshApp.kt            # 底部导航 + NavHost
    ├── GshViewModel.kt      # 状态管理
    ├── TodayScreen.kt       # 今日页
    ├── AddEntryScreen.kt    # 添加记录页
    ├── FoodLibraryScreen.kt # 食物库页
    ├── HistoryScreen.kt     # 历史页
    └── SettingsScreen.kt    # 设置页
```
