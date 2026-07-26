import test from "node:test";
import assert from "node:assert/strict";
import { RailSpatialIndex } from "../../core/src/main/resources/web/assets/spatial-index.mjs";

const lines = [
  { componentId: "a", points: [[0, 64, 0], [100, 64, 0]] },
  { componentId: "b", points: [[300, 64, 300], [420, 64, 300]] },
  { componentId: "c", points: [[-80, 64, -20], [-40, 64, 30]] },
];

test("spatial index returns only nearby line candidates", () => {
  const index = new RailSpatialIndex(64);
  index.rebuild(lines);
  assert.deepEqual(index.queryBox({ minX: -5, minZ: -5, maxX: 10, maxZ: 5 }).map((line) => line.componentId), ["a"]);
});

test("nearest line respects tolerance", () => {
  const index = new RailSpatialIndex(64);
  index.rebuild(lines);
  assert.equal(index.nearest(50, 2, 4)?.componentId, "a");
  assert.equal(index.nearest(50, 10, 4), null);
});
