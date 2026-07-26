import test from "node:test";
import assert from "node:assert/strict";
import {
  createInitialState,
  createStore,
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
