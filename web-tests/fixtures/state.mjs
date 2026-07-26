export const fixtureState = {
  ok: true,
  admin: false,
  runtime: {
    phase: "ready",
    scannedChunks: 0,
    remainingChunks: 0,
    pendingChunks: 0,
    cachedChunks: 42,
    cachedRails: 135,
    lastScanCompletedAt: 1752300000000,
    lastRenderCompletedAt: 1752300005000
  },
  background: { world: "world", centerX: 0, centerZ: 0, pixelsPerBlock: 1, imageUrl: "/background.png" },
  bounds: { minX: -180, minZ: -150, maxX: 220, maxZ: 190 },
  routes: [
    { id: "main-line", name: "主线", color: "#22c55e", lineWidth: 3, autoMatch: true, componentIds: ["world:component:main"] },
    { id: "harbor", name: "港区支线", color: "#0ea5e9", lineWidth: 3, autoMatch: true, componentIds: ["world:component:harbor"] }
  ],
  stations: [
    { id: "central", name: "中央站", world: "world", minX: -28, minY: 50, minZ: -18, maxX: 28, maxY: 82, maxZ: 18 }
  ],
  masks: [],
  hiddenLines: [],
  components: [
    { id: "world:component:main", world: "world", pointCount: 48, length: 312, minX: -160, minY: 63, minZ: -90, maxX: 185, maxY: 67, maxZ: 10, routeId: "main-line", routeName: "主线" },
    { id: "world:component:harbor", world: "world", pointCount: 22, length: 148, minX: 20, minY: 63, minZ: 4, maxX: 160, maxY: 65, maxZ: 98, routeId: "harbor", routeName: "港区支线" },
    { id: "world:component:yard", world: "world", pointCount: 16, length: 76, minX: -70, minY: 63, minZ: 20, maxX: 5, maxY: 64, maxZ: 70, routeId: "", routeName: "" }
  ],
  lines: [
    { componentId: "world:component:main", world: "world", type: "rail", powered: false, routeId: "main-line", routeName: "主线", color: "#22c55e", lineWidth: 3, points: [[-160, 64, -90], [-80, 64, -42], [0, 64, 0], [90, 65, 5], [185, 65, 10]] },
    { componentId: "world:component:harbor", world: "world", type: "powered-rail", powered: true, routeId: "harbor", routeName: "港区支线", color: "#0ea5e9", lineWidth: 5, points: [[20, 64, 4], [60, 64, 28], [105, 64, 55], [150, 64, 98]] },
    { componentId: "world:component:yard", world: "world", type: "rail", powered: false, routeId: "", routeName: "", color: "#f59e0b", lineWidth: 3, points: [[-70, 64, 20], [-38, 64, 42], [5, 64, 70]] }
  ]
};

export function adminFixture() {
  return {
    ...structuredClone(fixtureState),
    admin: true,
    masks: [{ id: "machine", name: "机器轨道", world: "world", enabled: true, minX: -145, minY: 0, minZ: 88, maxX: -112, maxY: 320, maxZ: 120 }],
    hiddenLines: [{ id: "hide-old", name: "废弃测试线", enabled: true, routeIds: [], componentIds: ["world:component:test"] }]
  };
}
