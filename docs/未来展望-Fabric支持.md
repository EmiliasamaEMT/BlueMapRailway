# 未来展望：Fabric 支持

本文档记录 BlueMapRailway 未来支持 Fabric 服务端的实现方向。当前正式产物仍是 Paper 插件；Fabric 支持会作为后续平台适配目标推进。

## 背景

服务器侧还有 Minecraft 1.21.11 Fabric 核心的使用场景。BlueMap 官方已经提供 Fabric 版本，因此铁路覆盖层理论上可以在 Fabric 服务器上通过 BlueMap API 输出 MarkerSet。

但当前 BlueMapRailway 直接依赖 Bukkit/Paper API，包括：

- `JavaPlugin`
- `Bukkit` 调度器
- `World`、`Chunk`、`Material`
- `BlockData`、`Rail.Shape`、`Powerable`
- Bukkit 事件系统
- Bukkit `YamlConfiguration`

因此不能简单把现有 jar 放进 Fabric `mods/` 目录。更合理的方式是拆分为公共核心和平台适配层。

## 目标产物

未来 release 可以同时提供两个 jar：

```text
BlueMapRailway-paper-<version>.jar
BlueMapRailway-fabric-1.21.11-<version>.jar
```

Paper 版放入：

```text
plugins/
```

Fabric 版放入：

```text
mods/
```

两个版本共享铁路识别、图构建、过滤、缓存、线路归类、站点和 SVG 导出逻辑，只在平台入口、世界读取、事件监听、命令和调度层分开。

## 建议模块结构

```text
BlueMapRailway
  common
    model
    graph
    filter
    cache
    route
    station
    exporter
    bluemap-render
  paper
    JavaPlugin 入口
    Bukkit 世界/区块/方块读取
    Bukkit 事件监听
    Bukkit 命令
    Bukkit 调度器
  fabric
    ModInitializer 入口
    Fabric/Minecraft 世界/区块/方块读取
    Fabric 事件监听
    Brigadier 命令
    ServerTick 调度器
```

## 公共核心边界

公共核心不应依赖 Bukkit 或 Fabric 类型。需要先抽象出平台无关接口：

```text
RailWorld
RailChunk
RailBlock
RailBlockState
RailScheduler
RailLogger
RailConfig
```

核心层只关心：

- 世界名。
- chunk 坐标。
- 方块坐标。
- 是否为 4 种原版铁轨。
- 铁轨形态。
- 是否通电。
- 当前扫描批次。

平台层负责把 Paper/Fabric 的原生对象转换成这些简单数据。

## Fabric 侧关键适配点

### 入口

Fabric 版应使用 `ModInitializer`，在服务器启动后初始化服务，并通过 BlueMap API 生命周期接入：

```text
BlueMapAPI.onEnable(...)
BlueMapAPI.onDisable(...)
```

### 方块读取

Fabric 侧需要基于 Minecraft 原生类读取：

- `ServerLevel`
- `LevelChunk`
- `BlockState`
- `Blocks.RAIL`
- `Blocks.POWERED_RAIL`
- `Blocks.DETECTOR_RAIL`
- `Blocks.ACTIVATOR_RAIL`

铁轨形态来自对应 rail block state property。需要把 Fabric/Minecraft 的 shape 枚举映射到公共核心自己的 `RailShape`。

### 调度

Paper 当前使用 Bukkit scheduler，每 tick 扫描少量 chunk。Fabric 可使用：

```text
ServerTickEvents.END_SERVER_TICK
```

维护一个扫描任务队列，在每个 server tick 处理 `chunks-per-tick` 个 chunk。

### Chunk 加载监听

Paper 当前使用 `ChunkLoadEvent`。Fabric 侧应寻找稳定的 chunk load callback；如果 Fabric API 对目标版本没有直接事件，可以考虑：

- 使用 Fabric lifecycle/server world events。
- 在 tick 中观察已加载 chunk 集合变化。
- 必要时使用 mixin，但 mixin 应作为后选方案。

优先选择 Fabric API 的公开事件，减少与 Minecraft 内部实现绑定。

### 命令

Fabric 命令建议使用 Brigadier 注册：

```text
/railmap status
/railmap debug
/railmap reload
/railmap rescan
/railmap route ...
```

命令行为尽量与 Paper 保持一致。`assign-nearest` 需要从 Fabric 的命令源解析玩家和坐标。

### 配置

当前使用 Bukkit `YamlConfiguration`。多平台后建议换成跨平台配置方案，例如：

- Configurate YAML
- SnakeYAML

目标是让 Paper 和 Fabric 使用同样的 `config.yml`、`routes.yml`、`stations.yml` 和缓存格式。

## BlueMap 兼容策略

BlueMap Marker API 是两个平台共享的关键依赖。Fabric 支持应尽量只使用公开的 `de.bluecolored.bluemap.api`，避免依赖 BlueMap 内部 `common` 或平台实现包。

版本策略建议：

- Paper 当前继续锁定已验证的 BlueMap API 版本。
- Fabric 1.21.11 先以 BlueMap 5.14+ 或 5.16 对应 API 为目标做编译验证。
- 如果 BlueMap API 在小版本间兼容，发布说明中写明最低 BlueMap 版本。

不建议直接接入 BlueMap 的内部 region 更新队列。BlueMap 的更新机制主要面向地图瓦片渲染，而铁路覆盖层需要独立维护铁轨扫描缓存和 MarkerSet。

## 推进顺序

1. 将现有 Paper 代码拆成 `common` + `paper`，保证 Paper 版功能不变。
2. 把 Bukkit 类型从核心模型中移除，建立平台无关的 `RailType`、`RailShape` 和 block state 描述。
3. 替换 Bukkit YAML 配置依赖。
4. 增加 Fabric Gradle/Loom 模块，先实现启动、配置加载和 BlueMap API 接入。
5. 实现 Fabric 方块读取和完整扫描。
6. 实现 Fabric chunk 加载触发扫描。
7. 实现 Fabric 命令。
8. 调整 GitHub Actions，release 同时上传 Paper 和 Fabric 两个 jar。
9. 在真实 Fabric 1.21.11 + BlueMap 环境测试。

## 风险

- Minecraft/Fabric/Yarn 命名在版本间变化较快，Fabric 侧维护成本高于 Paper。
- 铁轨 shape 和 powered 属性需要逐一映射，不能直接复用 Bukkit `Rail.Shape`。
- Fabric chunk load 事件可能不像 Bukkit 一样直接，需要确认目标 Fabric API 能力。
- 多模块改造会触及大量包结构，最好在 `0.2.0` 分支推进，避免影响当前 Paper 稳定版。

## 暂定结论

Fabric 支持可行，但应作为平台化改造推进，不建议在现有 Paper-only 工程里硬塞 Fabric 入口。短期优先保持 Paper 版可用；中期将公共核心抽出来；长期让 release 同时产出 Paper 和 Fabric 两个平台 jar。
