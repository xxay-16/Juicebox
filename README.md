# Juicebox（Windows / JDK 25 性能优化版）

基于上游 [aidenlab/Juicebox](https://github.com/aidenlab/juicebox) v2.17.00（commit `c7b6988`），在 Windows 上以 JDK 25 + Ant 构建，针对装配编辑（JBAT 工作流）与热图渲染做了多轮性能优化。

版本对照：基线 `b17f0e8` → 优化主提交 `f8ea3fb` → 当前 `cf1eeba`（含模块化重构）。

## 项目结构（模块化）

```
juicebox-core/     无 UI 依赖的核心模块（Gradle 构建 + JUnit 5 测试）
  core.data        Block、ContactRecord（列式存储）
  core.assembly    AssemblyTransform（装配坐标变换）、ScaffoldData
  core.io          BinReader（.hic 块解析）、BlockNormalizer（归一化）
src/juicebox/      桌面应用（Ant 构建），通过 core 模块获取算法
```

- **构建核心模块并跑测试**：`gradle :juicebox-core:build`（Gradle 9，JDK 25 工具链）
- **构建桌面应用**：Ant（见下），app 的源码路径已包含 core 源码

## 下载（Release v2.17.00-perf）

**绿色便携版（推荐，无需安装 Java）**：
[Juicebox-portable-win64.zip](https://github.com/xxay-16/Juicebox/releases/download/v2.17.00-perf/Juicebox-portable-win64.zip)（64 MB）
解压后双击 `启动-Juicebox.bat` 即可使用，内置 jlink 精简 JDK 25 运行时。

**仅应用 JAR（需自备 JDK 8+）**：
[Juicebox.jar](https://github.com/xxay-16/Juicebox/releases/download/v2.17.00-perf/Juicebox.jar)（36 MB）

发布页：https://github.com/xxay-16/Juicebox/releases/tag/v2.17.00-perf

## 构建

```bat
set JAVA_HOME=D:/runtime/jdk-25
ant -Dskip.tests=true -Djdk.home.1.8=D:/runtime/jdk-25 all
```

GUI 产物：`out/artifacts/Juicebox_jar/Juicebox.jar`（uber-JAR 已包含 commons-math3，缺失会导致读取 .hic 时 `NoClassDefFoundError`）。

运行：

```bat
D:/runtime/jdk-25/bin/javaw.exe -jar out/artifacts/Juicebox_jar/Juicebox.jar <file.hic>
```

## 性能优化清单（均有等价性验证）

### 坐标变换与块缓存
- **modifyBlock 预计算 bin->bin 映射表**：按（scaffold 版本、binSize、hicMapScale）失效重建；修复了 scaffold 数量守卫 bug（聚合为 1 个 scaffold 时表此前不会构建，导致每条记录仍走逐条二分）。变换约 5×（200 万条记录 ~180ms → ~25ms），输出与原实现逐位一致（含 AllByAll、精确起点、长度 1 scaffold、越界 bin 等边界用例）。
- **原始块缓存跨装配编辑保留**：移动/翻转 scaffold 后只失效变换后的块，不再重复磁盘 I/O + 解压 + 解析；编辑周期实测 159.7ms → ~2ms。
- **modifyBlock 恒等映射复用原 ContactRecord**：未移动的 scaffold 不再重复分配对象，单次剖析会话观测到 1.7GB 的对象分配由此消除。

### 列式存储（SoA，合并自 soa-block 分支）
- **Block 列式存储**：记录存于并行 `int[] binX/binY`、`float[] counts`（每条 ~12 字节，原 ~32 字节），`getContactRecords()` 保留为懒构建兼容视图。
- **数据阶段全数组化**：解析→归一化→装配变换走基本类型数组，modifyBlock 端到端 ~2.6×（25ms → ~10ms / 200 万条）。
- **渲染阶段全数组化**：全部 28 个渲染循环（主热图 + 26 个特殊模式）遍历数组，关联 key 由 `Block.getKey(index, norm)` 直接生成，不再物化 ContactRecord。
- **Observed 模式同色段批量填充**：同行同色连续段单次 `Arrays.fill` 写入。

### 渲染
- **热图 tile 直写像素**：contact 单像素填充直接写入 tile 栅格 `int[]`，绕过 Graphics2D 状态机；渲染突发期 fillRect 家族开销 ~18% → ~9.6%。
- **小地图（minimap）直写像素 + 状态缓存**：原实现每次全量重渲染最粗 zoom 整层（装配视图 9560×9560 bins、约 3800 万条记录）且走慢速 Graphics 路径，是加载后遮罩迟迟不解除的原因；现按视图状态缓存，渲染走直写路径。

### 解析与分配
- **BinReader / DatasetReaderV2 按实际记录密度预分配容量**（0.9 系数校准），常态块浪费降约 90% 且不再反复扩容拷贝。
- **Feature2DHandler.getNearbyFeatures 结果列表预分配**（EDT 绘制期不再反复扩容）。

### 并行
- **保守并行**：可见 tile 的数据块在工作线程池并行预取（磁盘 I/O + 解压 + 解析 + 装配变换），渲染保持在 EDT；数据阶段移出 EDT（样本占比 1.5% → 12.9%）。

### 实测效果（genome.hic 装配编辑会话，JFR 对比）
- 装配坐标查找 `lookUpOriginalAggregateScaffold`：174 样本 → 0（完全走数组查表）。
- `Object[]` 分配：702 次 / 1.8GB → 561 次 / 1.4GB（单次会话）。
- 编辑后重渲染开销主要集中在必要的 tile 重画（直写路径），无逐条二分。

## 验证方式

- `juicebox.tools.HiCTools dump` 输出与优化前逐字节一致（退出码 0）。
- modifyBlock 合成基准校验和与原实现一致；扩展表与兜底路径逐元素比对一致；各 zoom 块加载记录数与优化前逐一相同。
- GUI 在 `inter.hic` 与 `genome.hic` 上目检渲染正常（主热图、小地图、装配色块）。

## 说明

- 装配工作流请按 AGENTS.md 指引：`.assembly` 用 **Import Map Assembly**，`.review.assembly` 用 **Import Modified Assembly**。
- 上游 Juicebox 的使用文档见其官方 wiki；本仓库不维护通用使用文档。
