import test from "node:test";
import assert from "node:assert/strict";
import {
  createInitialState,
  createStore,
  runtimeNeedsRefresh,
  selectionWithToggle,
  setDirty,
} from "../../core/src/main/resources/web/assets/store.mjs";

test("store publishes reasons without mutating previous state", () => {
  const initial = createInitialState();
  const store = createStore(initial);
  const notifications = [];
  store.subscribe((state, reason) => notifications.push([state.activeTab, reason]));
  store.setState({ activeTab: "stations" }, "tab");
  assert.equal(initial.activeTab, "routes");
  assert.deepEqual(notifications, [["stations", "tab"]]);
});

test("selection toggles component IDs predictably", () => {
  const selected = selectionWithToggle(new Set(["a"]), "b", true);
  assert.deepEqual(Array.from(selected), ["a", "b"]);
  assert.deepEqual(Array.from(selectionWithToggle(selected, "a", true)), ["b"]);
});

test("dirty helper preserves other editors", () => {
  const state = createInitialState({ dirty: { route: false, station: true, mask: false } });
  assert.deepEqual(setDirty(state, "route", true), { dirty: { route: true, station: true, mask: false } });
});

test("runtime refresh follows data changes before marker rendering", () => {
  const loaded = { dataRevision: "a:1", lastRenderCompletedAt: 100 };
  const incoming = { dataRevision: "a:2", lastRenderCompletedAt: 100 };
  assert.equal(runtimeNeedsRefresh(loaded, incoming), true);
  // A failed load leaves the same loaded snapshot, so the next poll retries.
  assert.equal(runtimeNeedsRefresh(loaded, incoming), true);
  assert.equal(runtimeNeedsRefresh(incoming, incoming), false);
  assert.equal(runtimeNeedsRefresh(incoming, { dataRevision: "b:0" }), true);
});

test("older servers use the completed render timestamp", () => {
  assert.equal(runtimeNeedsRefresh(undefined, { lastRenderCompletedAt: 100 }), true);
  assert.equal(runtimeNeedsRefresh({ lastRenderCompletedAt: 100 }, { lastRenderCompletedAt: 100 }), false);
});
