# BlueMapRailway

BlueMapRailway 是一个为 Minecraft 服务器设计的 BlueMap 铁路叠加层插件/模组项目，用来把原版铁路网络显示到 BlueMap 网页地图上，并提供线路命名、站点管理、隐藏误识别铁路、管理网页和可选 SVG 配置（默认关闭）等能力。

当前项目已经整理为多模块结构：

- `core`：平台无关的核心模型、扫描图构建、线路规则
- `paper`：Paper 服务端插件实现
- `fabric`：Fabric 服务端模组实现

## 0.2 系列更新约定

后续更新使用 `0.2.x` 版本号，当前正式版本为 `v0.2.1`。
SVG 模块暂不维护：保留 `export.svg` 配置项，默认关闭，不作为兼容性或发布验收要求。
已有服务器显式设置 `enabled: true` 的配置不会被覆盖；暂不使用 SVG 时请关闭该项。

后续 agent 请先阅读 [0.2 系列优化与重构路线图](docs/0.2系列优化与重构路线图.md)，再按 [Agent 执行手册](docs/0.2系列Agent执行手册.md) 执行当前阶段。[v0.2.1 已发布](https://github.com/EmiliasamaEMT/BlueMapRailway/releases/tag/v0.2.1)，构建、核心测试和管理网页浏览器回归均已完成；大规模性能、轨道事件和更完整的 Fabric 有线路场景仍属于后续迭代。

管理网页已在 `0.2.1` 完成重写：新工作台外观、手机面板、视口绘制与有界索引、线路分页。详见[执行记录](docs/执行记录/管理网页重写-2026-09-19.md)。

## 当前状态

### Paper

Paper 版是当前更稳定、功能更完整的主线实现。

已具备的主要能力：

- 扫描并显示 4 种原版铁轨
  - `RAIL`
  - `POWERED_RAIL`
  - `DETECTOR_RAIL`
  - `ACTIVATOR_RAIL`
- 在 BlueMap 上渲染铁路覆盖层
- 线路命名、改色、改线宽
- 线路自动延续匹配
- 站点区域与站点图层
- 隐藏线路、裁切规则
- 管理网页
- 历史扫描缓存
- 自动备份与手动备份
- 地理型 SVG 导出（配置保留且默认关闭，当前不属于 0.2.x 验收范围）

### Fabric

Fabric 版已经进入可运行 Beta 阶段，当前重点是追平 Paper 的扫描与线路管理体验。

已具备的主要能力：

- BlueMap 接入
- 基础扫描与铁路渲染
- 局部重扫
- chunk cache 历史缓存
- route / station / edits 数据层
- 管理网页后端
- Fabric 命令层
- 自动备份 / 手动备份
- 配置补齐
- SVG 配置保留但默认关闭（当前不属于 0.2.x 验收范围）

当前 Fabric 默认配置特点：

- 放置/拆除铁轨后按邻区块局部重扫
- 默认 10 秒防抖
- 默认开启区块加载后自动补扫
- 默认关闭 SVG 导出
- 默认 BlueMap 线宽为 `3`
- 内置一张 Fabric 专用默认底图供管理网页使用

## 项目目录

```text
BlueMapRailway/
  core/
  paper/
  fabric/
  docs/
```

## 构建

根目录执行：

```powershell
.\gradlew.bat build
```

如果只构建 Fabric：

```powershell
.\gradlew.bat :fabric:build
```

如果只构建 Paper：

```powershell
.\gradlew.bat :paper:build
```

构建产物通常位于：

```text
paper/build/libs/
fabric/build/libs/
```

## 使用方式

### Paper

将构建出的 Paper 插件 jar 放入服务器的：

```text
plugins/
```

### Fabric

将构建出的 Fabric 模组 jar 放入服务器的：

```text
mods/
```

并确保同时安装：

- Fabric Loader
- Fabric API
- BlueMap Fabric 版

## 配置文件位置

### Paper

```text
plugins/BlueMapRailway/
```

### Fabric

```text
config/bluemaprailway/
```

Fabric 常见文件：

```text
config/bluemaprailway/config.yml
config/bluemaprailway/routes.yml
config/bluemaprailway/stations.yml
config/bluemaprailway/edits.yml
config/bluemaprailway/cache/chunks/
```

## 管理能力概览

项目目前围绕以下几类管理能力展开：

- 线路管理
  - 命名
  - 颜色
  - 线宽
  - 自动延续
- 站点管理
  - 区域框选
  - 站点图层
  - 站内轨道拆分
- 编辑规则
  - 隐藏整条线路
  - 裁切误识别线路
- 管理网页
  - 浏览模式
  - 管理模式
  - 背景底图
- 数据安全
  - 自动备份
  - 手动备份

## 文档

- [使用文档](docs/使用文档.md)
- [Fabric版配置使用说明](docs/Fabric版配置使用说明.md)
- [技术设计](docs/技术设计.md)
- [管理网页架构](docs/管理网页架构.md)
- [管理网页改版基线](docs/管理网页改版基线.md)
- [Core与平台分层设计](docs/Core与平台分层设计.md)
- [Fabric版实施路线图](docs/Fabric版实施路线图.md)
- [未来展望-Fabric支持](docs/未来展望-Fabric支持.md)
- [未来展望-线路管理](docs/未来展望-线路管理.md)
- [迭代记录](docs/迭代记录.md)
- [0.2 系列优化与重构路线图](docs/0.2系列优化与重构路线图.md)
- [0.2 系列 Agent 执行手册](docs/0.2系列Agent执行手册.md)
- [P0 执行记录](docs/执行记录/P0-2026-09-17.md)
- [P1 执行记录](docs/执行记录/P1-2026-09-17.md)

## 当前建议

- 如果你想直接部署长期使用，优先选择 Paper 版
- 如果你想一起推进跨平台支持或提前测试新链路，可以使用 Fabric Beta
- 如果你要调 Fabric 配置，优先看：
  - [Fabric版配置使用说明](docs/Fabric版配置使用说明.md)

## 发布记录

当前仓库已经包含：

- Paper 正式发布线
- Fabric 预发布线

`v0.2.0`：[Paper 正式版与 Fabric Beta 同步发布](https://github.com/EmiliasamaEMT/BlueMapRailway/releases/tag/v0.2.0)。该版本包含管理规则刷新链路优化、运行状态版本号与分阶段耗时诊断、共享编辑规则处理器和核心 Java 回归测试。SVG 配置保留且默认关闭，不属于本版本验收范围。

`v0.2.1`：[管理网页重写版同步发布](https://github.com/EmiliasamaEMT/BlueMapRailway/releases/tag/v0.2.1)。该版本更新工作台视觉、移动端面板、地图视口绘制、线路分页、请求竞态保护和有界空间索引；SVG 配置仍保留且默认关闭。

Fabric 版本记录（含历史预发布版本）：

- `v0.2.0`
- `v0.2.1`
- `v0.1.16-fabric-beta.1`（历史版本）

## 说明

项目仍在持续迭代中。后续每次较大的功能变更、配置变更和平台层重构，都会同步更新文档与迭代记录。
