# BlueMapRailway

BlueMapRailway 是一个面向 Paper 26.1.2 服务端的 BlueMap 附属插件，目标是在 BlueMap 网页地图上显示原版 Minecraft 的铁路网络。

当前支持识别 4 种原版铁轨：

- 普通铁轨：`RAIL`
- 动力铁轨：`POWERED_RAIL`
- 探测铁轨：`DETECTOR_RAIL`
- 激活铁轨：`ACTIVATOR_RAIL`

## 当前状态

项目目前处于早期 MVP 阶段，已经具备基础链路：

- 等待 `BlueMapAPI` 可用后启动铁路覆盖层服务。
- 自动扫描配置世界中的已加载区块。
- 读取 Bukkit `Rail.Shape`，生成内部铁路节点。
- 生成铁路连通分量 `RailComponent` 和稳定 component ID。
- 将连续铁轨合并为 BlueMap `LineMarker`。
- 支持通过 `routes.yml` 按 component ID 给线路命名、改色和调整线宽。
- BlueMap 图层和 SVG 会按线路分组，未归类线路进入未分类分组。
- 支持 `/railmap route ...` 管理命令，减少手写线路配置成本。
- 支持 `stations.yml` 定义站点区域，并在 BlueMap/SVG 中显示站点。
- 支持历史扫描缓存，已扫描过的铁轨区块卸载后仍可继续显示。
- 扫描完成后导出地理型 SVG 线路图，方便网页展示或二次加工。
- 监听铁轨放置、破坏、物理和红石变化，并延迟触发重扫。
- 提供管理员命令查看状态、重载配置和触发重扫。

## 管理员命令

```text
/railmap status
/railmap debug
/railmap reload
/railmap rescan
/railmap route list
/railmap route info <id>
/railmap route create <id> <名称>
/railmap route color <id> <#RRGGBB>
/railmap route width <id> <宽度>
/railmap route assign-nearest <id> [半径]
```

这些命令只面向服务器管理员。插件的正式工作方式是自动维护 BlueMap 图层，不依赖玩家手动扫描。

## 构建与发布

Paper 服务端插件的发布形式是一个 `.jar` 文件。构建后的 `BlueMapRailway-版本号.jar` 放入服务端 `plugins/` 目录即可使用。

本地构建：

```bash
./gradlew build
```

Windows 下也可以使用：

```powershell
.\gradlew.bat build
```

构建产物位于：

```text
build/libs/
```

创建 GitHub Release：

```bash
git tag v0.1.0
git push origin v0.1.0
```

推送 `v*` tag 后，GitHub Actions 会自动构建 `BlueMapRailway-0.1.0.jar` 并发布到 GitHub Release。

## 配置说明

默认配置位于 `src/main/resources/config.yml`。

```yaml
worlds:
  world:
    enabled: true
    scan-radius: -1
```

`scan-radius: -1` 表示只扫描该世界当前已加载的区块。设置为非负数时，会以世界出生点为中心扫描指定半径内的已加载区块。当前版本不会主动加载未加载区块，避免启动时造成服务器压力。

```yaml
scanner:
  chunks-per-tick: 1
  update-debounce-ticks: 40
```

`chunks-per-tick` 控制每 tick 扫描多少区块。`update-debounce-ticks` 控制铁轨变化后延迟多久合并触发重扫。

```yaml
cache:
  enabled: true
  file: cache/rail-cache.yml
```

历史扫描缓存默认开启。插件会自动缓存已经扫描到铁轨的区块；之后即使这些区块不再被玩家加载，也会继续使用缓存参与 BlueMap 覆盖层和 SVG 输出。

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

过滤器分两类：`hide-short-lines` 用于全局屏蔽零碎短铁路；`hide-fragmented-plain-rail-below-min-y` 用于屏蔽疑似废弃矿井的地下普通铁轨。

```yaml
export:
  svg:
    enabled: true
    directory: export
    file: railways.svg
    title: BlueMap Railway
    padding: 16.0
    non-scaling-stroke: true
```

SVG 导出默认开启。每次扫描完成后会生成：

```text
plugins/BlueMapRailway/export/railways.svg
```

这是地理型 SVG，会按 Minecraft `x/z` 坐标投影到二维平面，并保留铁轨类型、世界名和通电状态等数据属性。

线路命名和配色位于插件运行目录生成的 `routes.yml`：

```yaml
routes:
  main-line:
    name: "主线"
    color: "#22c55e"
    line-width: 6
    components:
      - "world:component:example"
```

可通过 `/railmap debug` 查看扫描到的 component ID，再填入 `routes.yml`。

也可以站在铁路旁边执行：

```text
/railmap route assign-nearest main-line 16
```

插件会把半径内最近的 component 绑定到指定线路，并排队重扫。

站点区域位于插件运行目录生成的 `stations.yml`：

```yaml
stations:
  spawn:
    name: "出生点站"
    world: "world"
    area:
      type: box
      min: [120, 60, -30]
      max: [170, 75, 20]
```

## 文档

- [使用文档](docs/使用文档.md)
- [技术设计](docs/技术设计.md)
- [未来展望：线路管理](docs/未来展望-线路管理.md)
- [迭代记录](docs/迭代记录.md)

后续每次实现较大功能时，都应同步更新相关文档。
