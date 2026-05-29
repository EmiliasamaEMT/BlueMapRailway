# Fabric 版配置使用说明

本文面向 `BlueMapRailway` 的 Fabric 版，说明：

- 配置文件位置
- 默认配置含义
- 每个配置项的用途
- 推荐配置方式
- 常见使用场景与排查思路

当前适用范围：

- Minecraft `1.21.11`
- Fabric Loader
- BlueMap Fabric
- `BlueMapRailway-fabric-0.1.16-fabric-beta.1` 及后续相近版本

## 1. 安装后文件位置

Fabric 版运行后，主要文件位于：

```text
config/bluemaprailway/
```

常见文件如下：

```text
config/bluemaprailway/config.yml
config/bluemaprailway/routes.yml
config/bluemaprailway/stations.yml
config/bluemaprailway/edits.yml
config/bluemaprailway/cache/rail-cache.yml
config/bluemaprailway/backups/
config/bluemaprailway/export/
```

含义：

- `config.yml`：主配置
- `routes.yml`：线路命名、颜色、线宽、自动延续
- `stations.yml`：站点区域
- `edits.yml`：裁切规则、隐藏规则
- `cache/rail-cache.yml`：历史扫描缓存
- `backups/`：自动或手动备份
- `export/`：SVG 导出目录

## 2. 默认配置

当前 Fabric 默认 `config.yml` 如下：

```yaml
worlds:
  "minecraft:overworld":
    enabled: true
    scan-radius: 8

scanner:
  chunk-load-rescan: true
  update-debounce-ticks: 200
  block-update-neighbor-radius: 1

cache:
  enabled: true
  file: cache/rail-cache.yml
  scan-newly-loaded-chunks: true
  chunk-load-debounce-ticks: 200

backup:
  enabled: true
  interval-hours: 24
  directory: backups
  include-config: true
  max-files: 0

filters:
  hide-short-lines: true
  short-line-max-points: 3
  short-line-max-length: 6.0
  hide-fragmented-plain-rail-below-min-y: true
  min-y: 50
  fragmented-line-max-points: 8
  fragmented-line-max-length: 32.0

markers:
  set-id: railways
  label: Railways
  default-hidden: false
  line-width: 3
  depth-test-enabled: false
  y-offset: 0.35
  colors:
    rail: "#9ca3af"
    powered-rail: "#22c55e"
    powered-rail-inactive: "#65a30d"
    detector-rail: "#f59e0b"
    activator-rail: "#ef4444"

export:
  svg:
    enabled: false

routes:
  auto-match:
    enabled: true
    anchor-radius: 16.0
    min-bounds-overlap-ratio: 0.35

stations:
  default-radius: 24.0
  default-y-radius: 6
  marker-set-label: "站点"
  bounds:
    enabled: true
    color: "#fb7185"
    line-width: 2
    depth-test-enabled: false
  internal-tracks:
    label: "站内轨道"
    default-hidden: false

admin-web:
  enabled: false
  host: 127.0.0.1
  port: 8766
  token: change-me
  background:
    image: admin-web/background.png
    world: "minecraft:overworld"
    center-x: 0.0
    center-z: 0.0
    pixels-per-block: 1.0
```

## 3. worlds：扫描哪些世界

示例：

```yaml
worlds:
  "minecraft:overworld":
    enabled: true
    scan-radius: 8
  "minecraft:the_nether":
    enabled: false
    scan-radius: 4
```

字段说明：

- `enabled`
  - 是否启用该世界扫描
- `scan-radius`
  - 冷启动时的初始种子扫描半径
  - 单位是“区块”，不是方块

当前 Fabric 版语义要点：

- 它**不再只是围绕出生点死扫**
- 现在主要扫描来源是：
  - 历史缓存里的区块
  - 当前已加载区块
  - 放置/拆除铁轨后进入队列的待更新区块
- `scan-radius` 更像：
  - **第一次启动、还没有缓存时的初始种子范围**

推荐：

- 大地图服：`8` 到 `16`
- 小型测试服：`4` 到 `8`
- 如果已经有稳定缓存，可以保持较小值

## 4. scanner：扫描触发与防抖

示例：

```yaml
scanner:
  chunk-load-rescan: true
  update-debounce-ticks: 200
  block-update-neighbor-radius: 1
```

### 4.1 `chunk-load-rescan`

是否允许“区块加载后自动扫描”。

- `true`：玩家跑图、重新加载区块时，会把新区块纳入扫描
- `false`：只靠已有缓存、手动重扫、或放置/拆除铁轨更新

推荐：

- 正常服：`true`
- 非常保守、只想尽量减负载：可以改成 `false`

### 4.2 `update-debounce-ticks`

铁轨变动后的更新防抖时间，单位是 tick。

换算：

- `20 tick = 1 秒`
- `200 tick = 10 秒`

当前默认：

- `200`，也就是 **10 秒**

这会影响：

- 玩家放置铁轨
- 玩家拆除铁轨
- 多次连续施工的合并扫描

含义：

- 不是每放一根就立刻扫
- 而是把一段时间内的多次修改合并成一轮局部扫描

推荐：

- 稳定生产服：`200`
- 想更灵敏：`100`
- 想更保守：`300`

### 4.3 `block-update-neighbor-radius`

铁轨更新时，局部重扫要带多少邻近区块。

当前默认：

```yaml
block-update-neighbor-radius: 1
```

含义：

- 扫“当前区块 + 四邻区块”

这是一个折中值：

- `0`：只扫当前区块，更省
- `1`：当前区块 + 邻区块，更稳

推荐：

- 大多数情况保持 `1`

## 5. cache：历史扫描缓存

示例：

```yaml
cache:
  enabled: true
  file: cache/rail-cache.yml
  scan-newly-loaded-chunks: true
  chunk-load-debounce-ticks: 200
```

### 5.1 `enabled`

是否启用铁路区块历史缓存。

建议始终开启：

- 关闭后，远离当前加载区域的线路更容易消失
- 开启后，之前扫过的铁路区块可以长期保留结果

### 5.2 `file`

缓存文件路径，相对 `config/bluemaprailway/`。

默认：

```yaml
file: cache/rail-cache.yml
```

一般不需要改。

### 5.3 `scan-newly-loaded-chunks`

新区块加载后，是否自动把它们加入扫描队列。

- `true`：会随着玩家活动逐步补全地图
- `false`：区块加载不自动扩充扫描结果

推荐：

- 正常服：`true`

### 5.4 `chunk-load-debounce-ticks`

区块加载类扫描的防抖时间。

默认：

```yaml
chunk-load-debounce-ticks: 200
```

也就是 **10 秒**。

作用：

- 玩家快速移动时，不会每加载一个 chunk 就扫一次
- 而是把一批新 chunk 合并后再扫

## 6. backup：自动备份

示例：

```yaml
backup:
  enabled: true
  interval-hours: 24
  directory: backups
  include-config: true
  max-files: 0
```

说明：

- `enabled`：是否开启自动备份
- `interval-hours`：间隔多少小时检查并生成备份
- `directory`：备份目录
- `include-config`：是否把 `config.yml` 一起打包
- `max-files`：最多保留多少份

特别说明：

- `max-files: 0` 表示**无限保留**
- 这是当前默认值

手动备份命令：

```text
/railmap backup
```

## 7. filters：线路过滤

示例：

```yaml
filters:
  hide-short-lines: true
  short-line-max-points: 3
  short-line-max-length: 6.0
  hide-fragmented-plain-rail-below-min-y: true
  min-y: 50
  fragmented-line-max-points: 8
  fragmented-line-max-length: 32.0
```

### 7.1 全局短线过滤

- `hide-short-lines`
- `short-line-max-points`
- `short-line-max-length`

作用：

- 屏蔽全图中非常零碎、非常短的小段铁路
- 减少误识别机器铁轨和碎片轨道

### 7.2 地下碎普通铁轨过滤

- `hide-fragmented-plain-rail-below-min-y`
- `min-y`
- `fragmented-line-max-points`
- `fragmented-line-max-length`

作用：

- 默认偏向屏蔽低于某高度的、很碎的普通铁轨
- 主要针对地下废弃矿井、碎片轨道

当前默认逻辑倾向于隐藏：

- 低于 `min-y`
- 只有 `RAIL`
- 没有其他功能轨
- 而且长度和点数都很碎

## 8. markers：BlueMap 叠加层显示

示例：

```yaml
markers:
  set-id: railways
  label: Railways
  default-hidden: false
  line-width: 3
  depth-test-enabled: false
  y-offset: 0.35
```

### 8.1 `set-id`

BlueMap 图层组的基础 ID。

默认：

```yaml
set-id: railways
```

后续会生成类似：

```text
railways.unclassified
railways.route.main-line
railways.stations
railways.station-internal
```

### 8.2 `label`

未分类线路图层的基础显示名。

### 8.3 `default-hidden`

图层是否默认隐藏。

- `false`：默认直接显示
- `true`：默认隐藏，要手动在 BlueMap 中打开

### 8.4 `line-width`

全局默认线宽。

当前默认已改为：

```yaml
line-width: 3
```

说明：

- 这个值会影响 BlueMap 叠加层里的线路粗细
- 如果某条 route 单独设置了 `line-width`，会覆盖这里

### 8.5 `depth-test-enabled`

是否启用深度测试。

一般建议保持：

```yaml
depth-test-enabled: false
```

这样铁路覆盖层更稳定，不容易被地形遮掉。

### 8.6 `y-offset`

线路在 BlueMap 上抬高一点点，避免和地面完全重合。

默认：

```yaml
y-offset: 0.35
```

## 9. markers.colors：不同轨道类型颜色

示例：

```yaml
colors:
  rail: "#9ca3af"
  powered-rail: "#22c55e"
  powered-rail-inactive: "#65a30d"
  detector-rail: "#f59e0b"
  activator-rail: "#ef4444"
```

说明：

- 未命名线路默认按轨道类型上色
- 已命名线路如果设置了 route 颜色，则整条线路统一使用 route 颜色

## 10. export：SVG 导出

示例：

```yaml
export:
  svg:
    enabled: false
```

当前默认：

- **关闭**

原因：

- 减少不必要的磁盘写入
- 减少扫描后额外导出开销
- 减少控制台和日志里的 SVG 导出信息

如果要开启：

```yaml
export:
  svg:
    enabled: true
```

开启后会在：

```text
config/bluemaprailway/export/railways.svg
```

生成导出文件。

## 11. routes.auto-match：线路自动延续

示例：

```yaml
routes:
  auto-match:
    enabled: true
    anchor-radius: 16.0
    min-bounds-overlap-ratio: 0.35
```

含义：

- 线路延长后，如果 component ID 变化
- 插件会尝试用旧绑定、锚点、范围重叠去匹配新 component

字段说明：

- `enabled`
  - 全局总开关
- `anchor-radius`
  - 锚点附近多远视为命中
- `min-bounds-overlap-ratio`
  - 旧 bounds 与新 component bounds 的最小重叠阈值

适用场景：

- 主线小幅延长
- 局部修线
- component ID 变化但线路本体基本不变

## 12. stations：站点显示

示例：

```yaml
stations:
  default-radius: 24.0
  default-y-radius: 6
  marker-set-label: "站点"
  bounds:
    enabled: true
    color: "#fb7185"
    line-width: 2
    depth-test-enabled: false
  internal-tracks:
    label: "站内轨道"
    default-hidden: false
```

字段说明：

- `default-radius`
  - 站点默认水平半径
- `default-y-radius`
  - 站点默认上下高度半径
- `marker-set-label`
  - 站点图层名

### 12.1 `bounds`

站点范围框的显示设置：

- `enabled`
- `color`
- `line-width`
- `depth-test-enabled`

### 12.2 `internal-tracks`

站内轨道图层设置：

- `label`
- `default-hidden`

作用：

- 站点区域内的线路段可以拆到单独图层
- 避免站场轨道把主线显示弄得太乱

## 13. admin-web：管理网页

示例：

```yaml
admin-web:
  enabled: false
  host: 127.0.0.1
  port: 8766
  token: change-me
  background:
    image: admin-web/background.png
    world: "minecraft:overworld"
    center-x: 0.0
    center-z: 0.0
    pixels-per-block: 1.0
```

### 13.1 基础开关

- `enabled`
  - 是否启用网页管理端
- `host`
  - 监听地址
- `port`
  - 监听端口
- `token`
  - 管理模式密钥

建议：

- 本机测试保持 `127.0.0.1`
- 如果要给局域网或隧道访问，再改成 `0.0.0.0`

### 13.2 background

- `image`
  - 自定义底图路径，基于 `config/bluemaprailway/`
- `world`
  - 这张底图对应哪个世界
- `center-x`
  - 底图中心 X
- `center-z`
  - 底图中心 Z
- `pixels-per-block`
  - 每格像素比例

当前默认行为：

- 如果 `config/bluemaprailway/admin-web/background.png` 存在
  - 优先使用这个服务器本地底图
- 如果不存在
  - Fabric 版会使用 jar 内置底图：
    - 范围约 `+-10000`
    - `1:1`
    - Fabric 专属默认图，不覆盖 Paper 那张

## 14. 配置补齐行为

Fabric 版现在支持缺失配置自动补齐。

触发时机：

- 模组启动时
- 执行重载时

行为特点：

- 只补缺失项
- 不覆盖你已经改过的现有值
- `worlds` 已有自定义时，不会强行把默认世界塞回去

## 15. 推荐配置方案

### 15.1 日常生产服

```yaml
scanner:
  chunk-load-rescan: true
  update-debounce-ticks: 200
  block-update-neighbor-radius: 1

cache:
  enabled: true
  scan-newly-loaded-chunks: true
  chunk-load-debounce-ticks: 200

export:
  svg:
    enabled: false

markers:
  line-width: 3
```

特点：

- 自动补扫
- 负载较保守
- 显示足够清楚

### 15.2 想更快看到更新

```yaml
scanner:
  update-debounce-ticks: 100

cache:
  chunk-load-debounce-ticks: 100
```

特点：

- 更新更快
- 扫描更频繁

### 15.3 极度保守减负载

```yaml
scanner:
  chunk-load-rescan: false
  update-debounce-ticks: 300

cache:
  scan-newly-loaded-chunks: false
  chunk-load-debounce-ticks: 300

export:
  svg:
    enabled: false
```

特点：

- 更安静
- 更省资源
- 地图更新会更慢，需要更多手动重扫

## 16. 常见问题

### 16.1 为什么线路没有立刻刷新？

先看：

- `scanner.update-debounce-ticks`
- `cache.chunk-load-debounce-ticks`

默认就是 **10 秒防抖**，不是立刻更新。

### 16.2 为什么远处线路不显示？

重点看：

- 历史上有没有扫到过这些区块
- `cache.enabled` 是否开启
- `scan-newly-loaded-chunks` 是否开启

当前 Fabric 版主链依赖：

- 已缓存区块
- 已加载区块
- 待更新区块

### 16.3 为什么网页背景不是我自己的图？

只有当下面这个文件存在时，才会优先使用它：

```text
config/bluemaprailway/admin-web/background.png
```

否则会使用 jar 内置默认底图。

### 16.4 为什么线路看起来太细？

改：

```yaml
markers:
  line-width: 3
```

或更粗一些：

```yaml
markers:
  line-width: 5
```

### 16.5 为什么没有 SVG 文件？

因为当前默认：

```yaml
export:
  svg:
    enabled: false
```

如果要导出，手动打开它。

## 17. 当前建议

如果你是第一次在 Fabric 上部署，建议直接保持默认值，先确认：

1. BlueMap 图层能显示
2. 放置/拆除铁轨后会在 10 秒左右更新
3. 新加载区块能自动纳入扫描
4. admin-web 底图坐标和你的世界对得上

确认这些都通了，再去微调线宽、防抖、过滤器和站点显示策略。
