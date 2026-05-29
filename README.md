# BlueMapRailway

BlueMapRailway 是一个为 Minecraft 服务器设计的 BlueMap 铁路叠加层插件/模组项目，用来把原版铁路网络显示到 BlueMap 网页地图上，并提供线路命名、站点管理、隐藏误识别铁路、SVG 导出、管理网页等能力。

当前项目已经整理为多模块结构：

- `core`：平台无关的核心模型、扫描图构建、线路规则
- `paper`：Paper 服务端插件实现
- `fabric`：Fabric 服务端模组实现

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
- 地理型 SVG 导出

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
- 可选 SVG 导出

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
config/bluemaprailway/cache/rail-cache.yml
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
- [Core与平台分层设计](docs/Core与平台分层设计.md)
- [Fabric版实施路线图](docs/Fabric版实施路线图.md)
- [未来展望-Fabric支持](docs/未来展望-Fabric支持.md)
- [未来展望-线路管理](docs/未来展望-线路管理.md)
- [迭代记录](docs/迭代记录.md)

## 当前建议

- 如果你想直接部署长期使用，优先选择 Paper 版
- 如果你想一起推进跨平台支持或提前测试新链路，可以使用 Fabric Beta
- 如果你要调 Fabric 配置，优先看：
  - [Fabric版配置使用说明](docs/Fabric版配置使用说明.md)

## 发布记录

当前仓库已经包含：

- Paper 正式发布线
- Fabric 预发布线

Fabric 当前已有预发布版本：

- `v0.1.16-fabric-beta.1`

## 说明

项目仍在持续迭代中。后续每次较大的功能变更、配置变更和平台层重构，都会同步更新文档与迭代记录。
