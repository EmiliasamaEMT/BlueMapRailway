import test from "node:test";
import assert from "node:assert/strict";
import {
  distanceToLineSquared,
  lineIntersectsBox,
  normalizeBox,
  pointInBox,
} from "../../core/src/main/resources/web/assets/geometry.mjs";

test("normalizeBox sorts and snaps coordinates", () => {
  assert.deepEqual(
    normalizeBox({ x: 12.9, z: -2.1 }, { x: 4.2, z: 8.8 }, true),
    { minX: 4, minZ: -3, maxX: 12, maxZ: 8 },
  );
});

test("line box intersection covers crossing segments", () => {
  const line = { points: [[-10, 64, 0], [10, 64, 0]] };
  assert.equal(lineIntersectsBox(line, { minX: -2, minZ: -2, maxX: 2, maxZ: 2 }), true);
  assert.equal(lineIntersectsBox(line, { minX: 20, minZ: 20, maxX: 30, maxZ: 30 }), false);
});

test("distance and point checks use Minecraft X/Z plane", () => {
  const line = { points: [[0, 10, 0], [10, 80, 0]] };
  assert.equal(distanceToLineSquared(line, 5, 3), 9);
  assert.equal(pointInBox(5, 3, { minX: 0, minZ: 0, maxX: 5, maxZ: 3 }), true);
});
