# Fabric 版实施路线图

本文档用于规划 BlueMapRailway 从当前单体 Paper 插件，演进为 `core + paper + fabric` 三层结构，并最终交付 1.21.11 Fabric 版本的实施路径。

## 1. 目标

目标不是简单复制一份 Paper 代码再改成 Fabric，而是：

- 抽离与平台无关的铁路扫描、线路建模、线路管理、站点管理、隐藏/裁切规则、SVG 导出等核心能力；
- 保留并稳定当前 Paper 版本；
- 在统一核心之上补出 Fabric 适配层；
- 最终同时发布 Paper 与 Fabric 两个版本，尽量保持配置格式、管理方式和渲染结果一致。

非目标：

- 第一阶段不追求一次性支持更多平台；
- 第一阶段不主动改变现有 YAML 数据格式；
- 第一阶段不重做 admin-web 前端交互，只在必要处做后端适配。

## 2. 当前现状

当前项目本质上是一个 Paper 插件，平台耦合较深，主要体现在：

- 启动入口依赖 `JavaPlugin`；
- 命令依赖 Bukkit 命令系统；
- 增量扫描依赖 Bukkit 事件，如区块加载、方块放置、方块破坏、红石变化；
- 铁轨读取依赖 Bukkit 的 `Material`、`BlockData`、`Rail`、`Powerable`；
- 配置与数据文件依赖 `YamlConfiguration`；
- 定时任务依赖 Bukkit 调度器；
- BlueMap 接入方式围绕 Paper 插件生命周期组织。

但当前也已经具备一批可以抽离为共享核心的能力：

- 铁路图模型；
- 图构建与线路拆分逻辑；
- 线路过滤逻辑；
- route、station、edit 规则；
- SVG 导出；
- admin-web 的前端静态资源与交互协议；
- 备份逻辑的大部分文件层语义。

## 3. 总体架构目标

建议将项目逐步演进为以下结构：

```text
BlueMapRailway/
  core/
  paper/
  fabric/
  docs/
```

建议职责划分如下：

### core

只放平台无关逻辑，不依赖 Bukkit / Paper / Fabric。

包含：

- `model/`
- `scan/`
- `route/`
- `station/`
- `edit/`
- `exporter/`
- `service/`
- `platform/` 接口定义

### paper

作为当前插件的 Paper 适配层，负责：

- 启动入口；
- 命令注册；
- 事件监听；
- Bukkit 调度；
- Bukkit 世界与方块访问；
- YAML 文件读写实现；
- Paper 侧 BlueMap 桥接；
- 当前发布产物。

### fabric

新增 Fabric 适配层，负责：

- Fabric mod 初始化；
- Fabric 事件与命令注册；
- Minecraft 原生世界与方块访问；
- 配置目录与数据目录管理；
- Fabric 侧 BlueMap 桥接；
- Fabric 发布产物。

## 4. 里程碑

建议按 4 个里程碑推进。

### M1：抽离 core，并让 Paper 版重新跑通

目标：

- 建立 `core` 模块；
- 将纯核心逻辑迁移到 `core`；
- `paper` 模块重新接回现有功能；
- 对现有服务器保持兼容。

完成标准：

- Paper 版行为与当前版本保持一致；
- `routes.yml`、`stations.yml`、`edits.yml`、`config.yml` 不需要迁移；
- BlueMap 图层结构与 SVG 输出结果基本一致；
- admin-web 继续可用。

### M2：Fabric 最小可运行版

目标：

- Fabric 模组可启动；
- 能读取配置与数据目录；
- 能执行完整扫描；
- 能在 BlueMap 中渲染基础铁路图层。

完成标准：

- Fabric 版可以启动并连接 BlueMap；
- 支持完整扫描；
- 支持基础图层渲染；
- 至少支持只读状态输出与基础 SVG 导出。

### M3：Fabric 功能追平核心能力

目标：

- route、station、mask、hidden line、自动延续等功能在 Fabric 上可用；
- admin-web 能在 Fabric 上正常工作；
- 备份、日志、配置补齐等辅助能力补齐。

完成标准：

- 与当前 Paper 版的主要使用流程一致；
- 管理网页可完成线路命名、站点管理、隐藏/裁切编辑；
- 线路重扫与增量扫描机制可用。

### M4：双平台发布与稳定化

目标：

- 形成双平台构建与发布流程；
- 文档按平台拆分；
- 补齐兼容性说明与已知问题列表。

完成标准：

- 可以同时构建 `paper` 与 `fabric` 产物；
- GitHub Release 支持双附件；
- 安装文档区分 Paper 与 Fabric；
- 有明确的平台差异说明。

## 5. 实施阶段

## 阶段 0：冻结当前行为基线

在拆代码前，先明确“什么叫没有拆坏”。

建议记录：

- 扫描后线路总数；
- component 数量；
- route 数量；
- station 数量；
- hidden rule / mask rule 数量；
- BlueMap 图层命名结构；
- SVG 导出中关键属性是否存在；
- admin-web 的主要读写接口行为。

建议准备一份固定测试数据：

- 含普通铁路、动力铁路、探测铁路、激活铁路；
- 含双轨、弯道、坡道、交叉、站场；
- 含误识别机器铁路与隐藏/裁切规则；
- 含命名线路、站点、自动延续配置。

这一阶段的意义是为后续回归提供稳定对照。

## 阶段 1：建立模块与依赖骨架

先只处理构建结构，不急着大搬代码。

建议先完成：

- 根项目多模块化；
- `core`、`paper`、`fabric` 三个 module；
- 公共版本号与发布名管理；
- 让 `paper` 先依赖 `core`；
- `fabric` 先建立空骨架，暂不接完整逻辑。

推荐顺序：

1. 调整 `settings.gradle.kts`
2. 根 `build.gradle.kts` 改为多模块组织
3. 新建 `core/build.gradle.kts`
4. 新建 `paper/build.gradle.kts`
5. 新建 `fabric/build.gradle.kts`
6. 先让 `paper` 成为当前唯一可发布产物

## 阶段 2：抽离平台接口

在 `core/platform/` 下先定义边界接口，再搬业务逻辑。

首批建议接口：

- `PlatformLogger`
- `PlatformScheduler`
- `PlatformPaths`
- `PlatformConfigStore`
- `PlatformWorldRegistry`
- `PlatformWorldView`
- `PlatformChunkView`
- `PlatformRailStateReader`
- `PlatformCommandSource`
- `PlatformPlayerLocator`
- `PlatformBlueMapBridge`

原则：

- `core` 里不再出现 `org.bukkit.*`
- `core` 里不再出现 `net.fabricmc.*`
- 接口尽量围绕“插件需要什么”定义，而不是直接映射底层平台对象

## 阶段 3：迁移共享模型与算法到 core

建议优先迁移这几类：

### 3.1 模型

- `RailLine`
- `RailComponent`
- `RailNode`
- `RailPosition`
- `RailScanResult`
- `RailRoute`
- `RailRouteAnchor`
- `RailRouteBounds`
- `RailStation`
- `RailEditMask`
- `RailEditHideRule`

### 3.2 扫描与图算法

- 铁轨节点解析后的内部表示；
- component 构建；
- 线段拆分；
- 零碎线路过滤；
- 废弃矿井疑似铁路过滤。

### 3.3 规则应用

- route 绑定与自动延续；
- station 范围归属；
- hidden / mask 规则；
- route 颜色与线宽覆盖逻辑。

### 3.4 导出与只读表示

- SVG 结构生成；
- admin-web 所需的 JSON 只读状态结构；
- 共用的简单序列化逻辑。

这一阶段应尽量把“数据处理逻辑”和“文件读写逻辑”拆开。

## 阶段 4：让 Paper 版通过适配层重建

在 `paper` 模块里重新接回：

- 启动入口；
- 命令；
- 事件监听；
- 调度器；
- YAML 读写；
- BlueMap 渲染桥；
- admin-web 启动；
- 自动备份；
- 配置缺失项补齐。

这一阶段的关键不是“新增功能”，而是：

- 当前 Paper 服继续可用；
- 老配置不炸；
- admin-web 行为不回退；
- 当前 release 能继续正常产出。

## 阶段 5：搭 Fabric 最小骨架

推荐先做到：

- Fabric mod 能启动；
- 能创建与读取数据目录；
- 能加载默认配置；
- 能连上 BlueMap；
- 能输出基础日志。

不建议一开始就把全部命令和网页都接上。

先把基础启动链路打通，再逐层加功能，会更稳。

## 阶段 6：实现 Fabric 扫描与增量更新

这是 Fabric 适配的核心难点。

要补的能力包括：

- 世界枚举；
- 区块枚举；
- 方块状态读取；
- 铁轨形状与通电状态转换；
- 区块加载后的增量扫描；
- 方块变化后的延迟重扫；
- 线程约束下的扫描调度。

建议此阶段先实现“正确”，再优化“实时性”。

如果某些 Bukkit 事件在 Fabric 上没有完全对等实现，可以暂时用更保守的重扫策略兜底。

## 阶段 7：迁移 Fabric 命令与管理能力

需要逐步接回：

- `/railmap` 管理命令；
- route 命名、颜色、宽度、自动延续；
- station 增删改；
- hidden line / mask 编辑；
- `/railmap backup`；
- `/railmap reload`；
- `/railmap log`。

同时要注意：

- Fabric 的权限模型与 Paper 不完全一致；
- 玩家定位与命令来源判断要重新实现；
- 命令错误提示要尽量与 Paper 版保持一致。

## 阶段 8：接回 admin-web

admin-web 前端大概率可复用，但后端要重新适配：

- HTTP server 启动方式；
- token 校验；
- 读取当前状态；
- 保存 route / station / edits；
- 触发重扫与刷新。

建议先保证：

- 浏览模式可用；
- 输入 token 后进入管理模式；
- 线路与站点列表可加载；
- route / station / hidden / mask 的增删改可用。

## 阶段 9：双平台发布体系

最终建议发布两个产物：

- `BlueMapRailway-paper-<version>.jar`
- `BlueMapRailway-fabric-<version>.jar`

同时补齐：

- GitHub Actions 构建矩阵；
- 双平台 Release 资产上传；
- 平台安装文档；
- 配置兼容说明；
- 已知差异说明。

## 6. 风险点

## 6.1 BlueMap Fabric 生命周期差异

BlueMap 虽然支持 Fabric，但生命周期与 Paper 插件式接入不完全相同。需要确认：

- 初始化时机；
- API 可用时机；
- 地图与世界对象的映射方式；
- 重绘与图层更新触发方式。

## 6.2 世界访问与线程模型

当前 Paper 实现中很多“看起来顺手”的调用，在 Fabric 上需要重新确认线程安全边界。尤其是：

- 区块访问；
- 方块状态读取；
- 异步扫描与主线程回写；
- 命令触发与世界状态同步。

## 6.3 配置读写兼容

当前代码高度依赖 Bukkit YAML API。Fabric 若仍保留 YAML，需要自行封装：

- 读取；
- 保存；
- 默认值补齐；
- 文件迁移；
- 容错与注释保留策略。

## 6.4 增量扫描事件并不完全等价

Paper 的区块加载、方块放置、方块破坏、红石变化监听组合，目前正好适合这个插件。Fabric 侧可能需要：

- 组合多个事件；
- 接受更宽松但更稳的重扫策略；
- 在性能和及时性之间重新平衡。

## 6.5 双平台维护成本

如果边界抽得不干净，后续每做一个功能都会变成：

- 改 Paper 一次；
- 改 Fabric 一次；
- 修 Paper 回归一次；
- 修 Fabric 回归一次。

因此前期必须先把 `core` 边界打稳。

## 7. 推荐开发顺序

推荐顺序如下：

1. 冻结当前 Paper 行为基线；
2. 多模块化；
3. 抽平台接口；
4. 迁移核心模型与算法到 `core`；
5. 让 `paper` 模块恢复现有功能；
6. 搭 `fabric` 最小启动骨架；
7. 先做 Fabric 基础扫描与基础渲染；
8. 再做 route / station / edit / admin-web；
9. 最后做双平台 release。

不推荐：

- 直接复制现有项目做第二套 Fabric 分叉；
- 在尚未抽离 core 的前提下同时修改 Paper 与 Fabric；
- 先把 admin-web 搬过去，再补底层扫描。

## 8. 阶段验收建议

每个阶段都应有最小验收标准：

### M1 验收

- Paper 启动正常；
- 现有命令正常；
- BlueMap 渲染正常；
- SVG 导出正常；
- admin-web 正常；
- 旧配置文件无需人工迁移。

### M2 验收

- Fabric 启动正常；
- 能执行完整扫描；
- BlueMap 上能显示基础线路；
- SVG 可导出。

### M3 验收

- route / station / hidden / mask 可用；
- admin-web 可写；
- 重扫与增量扫描可用；
- 自动备份与手动备份可用。

### M4 验收

- Paper / Fabric 双产物构建通过；
- Release 自动发布成功；
- 文档齐全；
- 已知差异有记录。

## 9. 时间预估

基于当前项目规模，保守估计：

- `core + paper` 重构回归：3 到 6 天
- Fabric 最小启动与扫描渲染：4 到 7 天
- Fabric 功能追平：5 到 10 天
- 测试、文档、发布：2 到 4 天

总体约为：

**2 到 4 周的工程量**

如果中途发现 BlueMap Fabric API 的生命周期与当前假设差异较大，时间可能继续上浮。

## 10. 下一步建议

路线图落地后的首个实际动作建议是：

1. 开 `refactor/core-paper-fabric` 分支；
2. 建好多模块骨架；
3. 先写 `core` 平台接口；
4. 选一小块纯逻辑先迁移，例如模型与线路过滤；
5. 确认 `paper` 模块仍能编译，再继续扩大迁移范围。

这样做可以把风险压在最前面，而不是等 Fabric 做到一半才发现 Paper 主干已经被拆乱。
