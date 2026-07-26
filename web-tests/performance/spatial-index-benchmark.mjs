import { performance } from "node:perf_hooks";
import { RailSpatialIndex } from "../../core/src/main/resources/web/assets/spatial-index.mjs";

const lineCount = Number(process.env.RAIL_LINE_COUNT || 100_000);
const lines = Array.from({ length: lineCount }, (_, index) => {
  const x = (index % 1000) * 8;
  const z = Math.floor(index / 1000) * 8;
  return {
    componentId: `component-${index}`,
    world: "world",
    points: [[x, 64, z], [x + 7, 64, z + (index % 3)]]
  };
});

const index = new RailSpatialIndex(128);
const buildStarted = performance.now();
index.rebuild(lines);
const buildMs = performance.now() - buildStarted;
const queryStarted = performance.now();
for (let query = 0; query < 10_000; query += 1) {
  const x = (query % 1000) * 8;
  const z = (query % 100) * 8;
  index.nearest(x, z, 10);
}
const queryMs = performance.now() - queryStarted;

console.log(JSON.stringify({ lineCount, buildMs: Math.round(buildMs), queryMs: Math.round(queryMs) }, null, 2));
