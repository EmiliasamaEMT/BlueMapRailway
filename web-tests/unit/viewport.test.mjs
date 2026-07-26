import test from "node:test";
import assert from "node:assert/strict";
import {
  centerView,
  fitView,
  screenToWorld,
  zoomView,
} from "../../core/src/main/resources/web/assets/viewport.mjs";

test("fitView keeps world bounds inside the viewport", () => {
  const view = fitView({ minX: 0, minZ: 0, maxX: 100, maxZ: 50 }, 1000, 500, 0);
  assert.equal(view.w, 100);
  assert.equal(view.h, 50);
});

test("zoom keeps cursor world position stable", () => {
  const original = { x: 0, y: 0, w: 100, h: 100 };
  assert.deepEqual(zoomView(original, { x: 25, z: 75 }, 0.5), { x: 12.5, y: 37.5, w: 50, h: 50 });
});

test("screen conversion and centering are deterministic", () => {
  const view = centerView({ x: 0, y: 0, w: 100, h: 50 }, 10, 20);
  assert.deepEqual(view, { x: -40, y: -5, w: 100, h: 50 });
  assert.deepEqual(screenToWorld(view, { left: 0, top: 0, width: 1000, height: 500 }, 500, 250), { x: 10, z: 20 });
});
