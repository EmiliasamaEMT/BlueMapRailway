import test from "node:test";
import assert from "node:assert/strict";
import { normalizeRailLineWidth } from "../../core/src/main/resources/web/assets/map-view.mjs";

test("rail line width uses API values and a three-pixel fallback", () => {
  assert.equal(normalizeRailLineWidth(5), 5);
  assert.equal(normalizeRailLineWidth("2"), 2);
  assert.equal(normalizeRailLineWidth(0), 3);
  assert.equal(normalizeRailLineWidth(undefined), 3);
});

test("rail line width stays within the supported canvas range", () => {
  assert.equal(normalizeRailLineWidth(0.25), 1);
  assert.equal(normalizeRailLineWidth(128), 64);
});
