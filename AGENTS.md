# GSH — 增肌饮食记录 App · Agent 交接文档

> 本文档面向未来接手本项目的 AI Agent，由 2026-10 会话整理。请先完整阅读再动手。

## 项目概况

面向增肌人群的三餐饮食记录 App（Android），主人/用户：shunhua.gong（个人使用，非商业）。
记录饮食 → 换算蛋白/碳水/脂肪 → 与"体重×每公斤系数"的目标对比 → AI 分析。

- 语言/UI：Kotlin 2.0.21 + Jetpack Compose（Material 3，BOM 2024.12.01）
- 数据：Room（`gsh.db`，version 4）+ SharedPreferences（SettingsStore）
- 构建：Gradle 8.11.1 / AGP 8.7.3，compileSdk 35，minSdk 26，Java 17
- 当前版本：versionName 1.7 / versionCode 8

## 常用命令

```bash
./gradlew :app:compileReleaseKotlin   # 编译验证（最快）
./gradlew :app:assembleRelease        # 构建 Release APK（keystore 签名）
adb install -r app/build/outputs/apk/release/app-release.apk  # 覆盖安装（保数据）
adb exec-out screencap -p > x.png     # 截屏验证 UI
```

- adb 路径：`/Users/bokken/Library/Android/sdk/platform-tools/adb`（不在 PATH）
- 测试设备：Xiaomi（序列号 c0a27283，屏幕 1440×3200），装的是 **release 包**
- **Debug 包签名与设备已装版本不匹配，无法覆盖安装**；只能装同 keystore 签名的 Release 包
- 设备 USB 连接不稳定，安装前先 `adb devices` 确认
- 无单元测试、无 CI；验证 = 编译 + 安装 + 截屏/日志

## Git 工作流（重要约定）

- 远端：`git@github.com:shunhuagong/GSH.git`（SSH，密钥 `~/.ssh/id_ed25519_github`）
- **`main` 分支保持空置**（只有最初建仓的 TEST README，commit a8cbfe6），**所有开发在 `develop` 分支**
- 提交身份（仅本仓库 local config）：`shunhua.gong <shunhua.gong@unity.cn>`
- 工作流：**改动 → 编译验证 → commit（中文消息）→ push develop → 构建 → adb 安装 → 截屏验证**
- 回退用 `git revert`（用户明确要求可回退），**禁止 force push**；每步独立提交便于精确回退
- ⚠️ `keystore/` 与 `keystore.properties` 已 gitignore，**严禁提交**（含签名密码）

## 架构速览

```
com.gsh.app
├── MainActivity          # 入口，collects dynamicColor → GshTheme
├── ai/AiAdvisor          # OpenAI 兼容客户端（OkHttp + org.json），单日分析 + 近7天周报 prompt
└── data/
    ├── Food / MealEntry  # 实体；MealEntry 存营养快照（历史不受食物库修改影响）
    ├── Photo / PhotoDao   # 打卡照片（kind：0 餐次 / 1 体型；path 指 filesDir/photos 私有文件）
    ├── AppDatabase       # version 4；注意：fallbackToDestructiveMigration 仍在（有数据风险，待移除）
    ├── SeedFoods         # 预置食物库（口径：USDA + 中国食物成分表）
    ├── GshRepository     # 唯一数据入口；seedFoods() 三同步：补插缺失/清理多余/数值修订；照片采样+EXIF 旋转矫正压到 1600px 存 filesDir/photos
    └── SettingsStore     # profile + AiConfig + dynamicColor，StateFlow 通知
ui/
├── GshApp                # NavHost + 底部导航（今日/历史/变化/食物库/设置）；页面转场动画；启动插画覆盖层（splash_illustration.webp，约1秒淡出，点击跳过，回前台不重播）
├── GshViewModel          # 单一 ViewModel；flatMapLatest 派生流；streak 打包计算
├── TodayScreen           # 三环营养卡+热量大数字+打卡徽章+confetti+餐次卡片+餐次照片缩略图+编辑数量对话框
├── BodyScreen            # 「变化」页：体型照片时间线（按日期分组，新→旧，拍照/相册）
├── Photos                # 拍照/相册选图启动器（免权限方案）+ 全屏大图查看器 + 缩略图
├── CopyMealScreen        # 全屏月历复制页（只能从历史拉到今天）
├── AddEntryScreen / FoodLibraryScreen / HistoryScreen / SettingsScreen
└── ConfettiOverlay / AiAnalysisSheet / Theme（含 ProteinColor/CarbsColor/FatColor 固定色）
```

## 业务口径与用户约定（勿"好心修复"）

1. **燕麦片蛋白 = 0**：用户记账口径只统计优质蛋白，燕麦仅作碳水来源。勿改回 15.0
2. **复制饮食只允许"历史 → 今天"**：入口只出现在今天视图，月历只能选今天前的日期；历史不可写入（用户明确的设计）
3. **肉类分生熟条目**（生重/熟重密度不同），食物按 100g/100ml/个 三种单位口径
4. **seedFoods() 数值修订机制**：修改 SeedFoods 任何条目的数值后，会在启动时自动同步更新设备库中同名非自定义条目（2026-10 新增）
5. **热量颜色规则**：`热量 >= 目标` → 红，否则绿（CarbsColor 固定绿，不受动态取色影响）；三个营养环超 100% 变红
6. **AI 服务**：OpenAI 兼容协议，默认智谱 BigModel `glm-4-flash`（免费），设置页可改 baseUrl/key/model；**用户的 API Key 尚未配置**，AI 报错属预期
7. **动态取色**：Material You（Android 12+ 跟随壁纸），设置页有开关，默认开
8. **数据安全**：手机上有用户真实饮食记录（2026-10-13 起），**严禁任何清数据/卸载/降级操作**；release 包不可 run-as，无法直接读 DB（用截屏验证）
9. 历史记录存营养快照，改食物库/删条目不影响已有记录
10. **照片**：餐次照片按餐次挂在今日页（📷 图标），体型照片进「变化」页；照片压缩存 App 私有目录（卸载即清，不进系统相册，拍照/选图均无需敏感权限）；从相册选的照片记为「添加当天」，不读拍摄时间

## 待办与候选功能（用户未完成，按优先级）

1. 历史统计图表（近30天蛋白/碳水达标率、训练日执行率）
2. 体重历史记录（当前体重是单一数值）
3. 训练日批量标记 / 按周几默认规则
4. AI 流式输出 + 追问对话
5. 添加「鸡腿肉（去皮带骨，生）」条目（100g：蛋白12.5 / 碳水0 / 脂肪3.0）——已和用户讨论过口径，未确认添加
6. 工程加固：移除 fallbackToDestructiveMigration（改为显式迁移）、补单元测试（营养换算/比例折算）、食物库扩容

## 用户沟通偏好

- 全程中文；非专业开发者，解释避免堆术语
- **先给方案评估再动手**（用户常问"你觉得合理吗"）；重大决策用选项确认
- 小步迭代：每个改动 = 编译 → 提交 → 安装 → 截屏验证，一气呵成
- 不满意就回退（git revert），从不用 APK 备份
- 营养学问题按增肌口径回答，参考《中国食物成分表》数值

## 版本历史

| 版本 | 内容 |
|------|------|
| v1.3 (code 4) | 贝贝南瓜入库、AI 分析（单日+周报）、点击记录编辑数量、移除 FAB |
| v1.4 (code 5) | 复制饮食、日期日历选择器、CSV/JSON 数据导出分享 |
| v1.5 (code 6) | 美化三件套：🔥打卡徽章 / 🎉全达标彩带 / 🌈动态取色；热量大数字突出（未达标绿/达标红）；复制改全屏月历页（仅今天入口） |
| v1.6 (code 7) | 拍照打卡：餐次 📷 照片（拍照/相册）；新增「变化」tab（体型照片时间线）；查看器左右滑动切换、连续删除（删一张自动停下一张，caption 显示第 n/m 张）；Room v3→v4（photos 表，显式迁移）；引入 Coil（加载）+ ExifInterface（旋转矫正） |
| v1.7 (code 8) | 启动页：即梦 AI 生成插画（用户自有素材，构图裁剪去除占位文字条与角标，仅个人自用）+ GSH 字样；约 1 秒淡出、点击跳过、回前台不重播；windowBackground 同步奶油色防闪白 |

（2026-10-16 之后的功能改动请追加到本表）
