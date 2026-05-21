# Core 与平台分层设计

本文档用于定义 BlueMapRailway 后续拆分为 `core + paper + fabric` 结构时的边界原则、模块职责与优先迁移对象。

## 1. 设计目标

分层设计的核心目标有三个：

1. 让线路扫描、线路管理、站点管理、隐藏/裁切、SVG 导出等业务能力只实现一次；
2. 让 Paper 与 Fabric 的差异收敛到少量平台适配代码；
3. 为后续双平台维护、测试和发布建立稳定结构。

## 2. 分层原则

## 2.1 core 只关心业务，不关心平台对象

`core` 中不应直接出现：

- `org.bukkit.*`
- `io.papermc.*`
- `net.fabricmc.*`
- Minecraft 服务端的具体平台包装对象

`core` 只接收抽象接口、普通 Java 对象、领域模型和纯数据结构。

## 2.2 平台层负责翻译，不负责重新发明业务逻辑

`paper` 与 `fabric` 模块的责任应是：

- 把平台对象翻译为 `core` 可理解的数据；
- 把 `core` 的输出翻译回 BlueMap、命令系统、配置文件、HTTP 服务等平台能力；
- 管理生命周期、事件和线程调度。

平台层不应自行实现一套与 `core` 重复的线路识别或规则应用逻辑。

## 2.3 文件格式尽量兼容现状

现有数据文件：

- `config.yml`
- `routes.yml`
- `stations.yml`
- `edits.yml`

应尽量继续沿用，避免因平台切换带来额外迁移成本。

如需对底层读写实现做替换，也应尽量保持文件结构不变。

## 2.4 先稳住 Paper，再接 Fabric

分层的第一验收对象不是 Fabric，而是：

**当前 Paper 版本在新结构下仍然正常工作。**

只有当 Paper 已经能通过适配层调用 `core`，后续的 Fabric 接入才会顺畅。

## 3. 建议模块职责

## 3.1 core

`core` 负责：

- 领域模型；
- 铁路图构建与线路拆分；
- 线路过滤；
- route 绑定与自动延续；
- station 归类与可视化数据准备；
- hidden line / mask 规则应用；
- SVG 导出；
- 管理网页所需的只读状态对象；
- 与平台交互的接口定义。

`core` 不负责：

- 读取 Bukkit / Fabric 世界对象；
- 命令注册；
- 事件监听；
- 直接读写 Bukkit YAML；
- 启动 HTTP 服务；
- 直接调用某个平台的调度器。

## 3.2 paper

`paper` 负责：

- `JavaPlugin` 启动入口；
- Bukkit 命令；
- Bukkit 事件；
- Bukkit 世界访问；
- Bukkit 定时任务；
- Bukkit / Paper YAML 配置访问；
- Paper 侧 BlueMap API 桥接；
- admin-web 启动；
- Paper 发布产物。

## 3.3 fabric

`fabric` 负责：

- Fabric mod 启动入口；
- Fabric 命令；
- Fabric 事件；
- Minecraft 原生世界访问；
- Fabric 环境下的调度；
- Fabric 配置文件与数据目录；
- Fabric 侧 BlueMap API 桥接；
- admin-web 启动；
- Fabric 发布产物。

## 4. 现有代码拆分建议

以下只是第一版建议，不代表一次性全部完成。

## 4.1 优先抽到 core 的内容

### 模型

建议优先迁移：

- `RailLine`
- `RailComponent`
- `RailConnection`
- `RailDirection`
- `RailGraphResult`
- `RailScanResult`
- `RailRoute`
- `RailRouteAnchor`
- `RailRouteBounds`
- `RailStation`
- `RailEditMask`
- `RailEditHideRule`

### 算法

- `RailGraphBuilder`
- `RailLineFilter`
- route 自动匹配逻辑
- station 归类逻辑
- hide / mask 应用逻辑

### 导出

- `SvgRailExporter` 的纯 SVG 结构生成部分
- `SimpleJson` 或等价轻量序列化辅助

## 4.2 先保留在 paper，后续再抽的内容

这些类当前平台耦合较强，可以先保留在 `paper`，等接口稳住再处理：

- `BlueMapRailwayPlugin`
- `RailwayBlockListener`
- `RailwayBackupService`
- `AdminWebServer`
- `ConfigUpdater`

## 4.3 需要拆分重构的内容

一些类不适合原样搬迁，应先拆为“核心逻辑 + 平台外壳”：

### RailwayService

当前问题：

- 同时承担平台调度、文件读写、命令服务、状态组织、规则写回、BlueMap 渲染触发等职责；
- 含有大量 Bukkit `Player`、`Location`、`YamlConfiguration`、`BukkitTask`。

建议拆为：

- `RailwayCoordinator` 或类似核心服务，放在 `core`
- `PaperRailwayFacade`，放在 `paper`
- `FabricRailwayFacade`，放在 `fabric`

### RailScanner

当前问题：

- 同时负责世界枚举、区块扫描、平台对象读取与业务组织。

建议拆为：

- `core` 中只保留扫描流程与结果组织；
- 世界枚举、区块遍历、方块读取委托给平台接口。

### RailBlockReader

当前问题：

- 输入直接依赖 Bukkit `Material` 与 `BlockData`。

建议改为：

- `core` 只识别抽象后的 `RailBlockSnapshot`
- Paper / Fabric 分别负责把底层方块状态翻译成快照对象

## 5. 建议抽象接口

以下接口是第一批最值得建立的边界。

## 5.1 PlatformLogger

职责：

- 常规 info 输出
- warning / error 输出
- 可选调试输出
- 与插件独立日志文件对接

目的：

- `core` 不直接依赖某个平台 logger。

## 5.2 PlatformScheduler

职责：

- 主线程立即执行
- 主线程延迟执行
- 周期任务
- 异步任务
- 取消任务

目的：

- 替代当前 Bukkit 调度器直接调用。

## 5.3 PlatformPaths

职责：

- 获取插件数据目录
- 获取缓存目录
- 获取日志目录
- 获取备份目录
- 获取 web 资源目录

目的：

- 统一文件路径获取方式。

## 5.4 PlatformConfigStore

职责：

- 读取配置文件
- 保存配置文件
- 创建默认配置
- 补齐缺失项
- 读取/写回 route、station、edit 数据

目的：

- 从 Bukkit `YamlConfiguration` 中脱身。

实现建议：

- `core` 面向抽象配置节点或 DTO
- 平台层负责 YAML 解析与序列化

## 5.5 PlatformWorldRegistry

职责：

- 枚举可扫描世界
- 通过名称查找世界
- 列出已加载世界

目的：

- 屏蔽 Bukkit / Fabric 世界注册差异。

## 5.6 PlatformWorldView

职责：

- 获取世界标识
- 获取最小 / 最大高度
- 枚举区块
- 查询区块是否已加载
- 读取某坐标方块状态

目的：

- 提供扫描层访问世界的最小必要能力。

## 5.7 PlatformPlayerLocator

职责：

- 获取玩家当前所在世界
- 获取玩家当前位置
- 计算与线路 / 站点的邻近关系

目的：

- 支撑 `assign-nearest`、`anchor-nearest`、站点范围设置等功能。

## 5.8 PlatformCommandSource

职责：

- 判断是玩家还是控制台
- 发送消息
- 检查权限

目的：

- 让 `core` 可表达命令意图，而不绑定具体命令系统。

## 5.9 PlatformBlueMapBridge

职责：

- 判断 BlueMap 是否可用
- 获取地图 / 世界映射
- 创建与更新图层
- 清理旧图层
- 写入线路、站点与可视化边框

目的：

- 将业务与 BlueMap 接口胶水隔开。

## 5.10 RailBlockSnapshot

这不是平台服务接口，而是非常关键的跨平台数据对象。

建议包含：

- `worldId`
- `x`
- `y`
- `z`
- `railType`
- `shape`
- `powered`

必要时可补：

- `waterlogged`
- `ascending`
- `extraFlags`

这样 `core` 就可以只围绕快照对象做铁路识别，而不再关心 Bukkit `BlockData` 或 Fabric `BlockState`。

## 6. 领域对象调整建议

## 6.1 RailPosition

当前如果仍持有 Bukkit `World`，应改为：

- `worldId: String`
- `x`
- `y`
- `z`

这样可以自然跨平台，也更方便序列化。

## 6.2 RailType

不要继续直接绑定 Bukkit `Material`。

建议改为纯领域枚举：

- `RAIL`
- `POWERED_RAIL`
- `DETECTOR_RAIL`
- `ACTIVATOR_RAIL`

平台层各自做从底层方块类型到 `RailType` 的映射。

## 6.3 RailNode

应只保留：

- 位置；
- 铁轨类型；
- 连接形状；
- 通电状态；
- 连接方向信息。

不直接携带平台对象。

## 7. 服务拆分建议

建议把当前“大服务”拆成更清晰的层次：

## 7.1 核心服务

例如：

- `ScanCoordinator`
- `RouteService`
- `StationService`
- `EditService`
- `ExportService`
- `WebStateService`

这些服务应只面向抽象接口和领域对象。

## 7.2 平台门面

例如：

- `PaperRailwayFacade`
- `FabricRailwayFacade`

负责：

- 调度调用顺序；
- 接命令；
- 接事件；
- 调用配置存储；
- 触发 BlueMap 刷新；
- 对接备份与日志。

## 8. 配置与数据层设计建议

现阶段不建议立即把 YAML 文件格式改成别的。

建议做法：

- 对 `config.yml`、`routes.yml`、`stations.yml`、`edits.yml` 分别定义内部 DTO；
- 平台层负责 YAML 与 DTO 之间转换；
- `core` 只消费 DTO。

这样后续如果 Fabric 端改用不同解析库，核心逻辑也不需要改。

## 9. admin-web 的分层建议

admin-web 建议拆成三层：

1. 前端静态资源  
   HTML / CSS / JS，可双平台共用

2. Web 状态 DTO  
   线路、站点、route、mask、hidden line 的 JSON 结构，放在 `core`

3. HTTP 宿主  
   Paper / Fabric 各自启动和鉴权，放在平台层

这样后续网页功能新增时，不会因为平台不同而改两次前端协议。

## 10. 备份与日志的分层建议

## 10.1 备份

备份逻辑可以拆成：

- `core`：决定哪些文件需要备份、备份命名规则、清理策略
- 平台层：实际拿路径、调度周期任务、执行 zip 写入

## 10.2 日志

日志建议保持：

- `core` 只调用 `PlatformLogger`
- 平台层决定是否输出到控制台、插件独立日志文件、调试日志等

## 11. 建议的迁移顺序

从分层角度看，推荐顺序如下：

1. 新建 `core` 模块
2. 先迁移纯模型
3. 迁移 `RailType`、`RailPosition` 等基础对象去平台化
4. 引入 `RailBlockSnapshot`
5. 迁移图构建与过滤算法
6. 迁移 route / station / edit 规则逻辑
7. Paper 侧接回
8. 再做 Fabric 侧实现

不建议一开始就迁移：

- 命令层
- admin-web 宿主
- 备份调度
- 复杂配置补齐逻辑

这些更适合在 `core` 边界成形后再接。

## 12. 第一批具体任务建议

如果马上开始动手，建议第一批任务是：

1. 建好多模块目录和构建骨架；
2. 在 `core` 新建 `model/`、`platform/`、`scan/`；
3. 把 `RailType` 改成纯领域枚举；
4. 把 `RailPosition` 改成仅保存 `worldId + xyz`；
5. 新建 `RailBlockSnapshot`；
6. 把 `RailGraphBuilder` 迁入 `core` 并改为只吃 `RailNode` / `RailBlockSnapshot`；
7. 让 `paper` 先实现一版快照翻译器；
8. 在此基础上验证 Paper 还能扫出与当前一致的线路结构。

## 13. 验收标准

核心分层是否成功，可以看这几个问题：

- `core` 是否完全不依赖 Bukkit / Fabric；
- 新增一个线路管理功能时，是否大部分代码只改 `core`；
- Paper 与 Fabric 是否只需各自补少量适配；
- 旧 Paper 功能是否没有明显回退；
- 配置与数据文件是否继续兼容现有服务器。

如果这些条件成立，说明这次分层是真正有价值的，不只是把文件搬了个地方。
