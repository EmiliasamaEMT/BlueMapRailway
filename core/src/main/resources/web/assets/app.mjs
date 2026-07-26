import { RailwayApi } from "./api.mjs";
import { lineBounds, normalizeBox } from "./geometry.mjs";
import { RailwayMapView } from "./map-view.mjs";
import {
  blankMaskDraft,
  blankRouteDraft,
  blankStationDraft,
  createInitialState,
  createStore,
  selectionWithToggle,
  setDirty,
} from "./store.mjs";
import { DashboardUI, shortComponentId } from "./ui.mjs";
import { centerView, fitView, panView, zoomView } from "./viewport.mjs";

const $ = (id) => document.getElementById(id);
const rememberedToken = localStorage.getItem("bluemaprailway-token") || "";
const savedLayers = readJsonSetting("bluemaprailway-layers", {});
const savedPreferences = readJsonSetting("bluemaprailway-edit-preferences", {});
const initial = createInitialState({
  layers: { ...createInitialState().layers, ...savedLayers, routes: savedLayers.routes || {} },
  preferences: { ...createInitialState().preferences, ...savedPreferences },
});
const store = createStore(initial);
const api = new RailwayApi(() => store.getState().token);
let ui;
let hasInitialFit = false;
let runtimeTimer = null;

const map = new RailwayMapView(store, {
  stage: $("map-stage"),
  backgroundCanvas: $("background-canvas"),
  railCanvas: $("rail-canvas"),
  overlay: $("edit-overlay"),
  maskLayer: $("mask-layer"),
  stationLayer: $("station-layer"),
  selectionLayer: $("selection-layer"),
  draftLayer: $("draft-layer"),
});

const handlers = {
  changeTab,
  focusRoute,
  editRoute,
  locateRoute,
  hideRoute,
  deleteRoute,
  focusComponent,
  locateComponent,
  focusStation,
  editStation,
  locateStation,
  deleteStation,
  focusMask,
  editMask,
  locateMask,
  deleteMask,
  focusHiddenLine,
  deleteHiddenLine,
  toggleRouteLayer,
};

ui = new DashboardUI(store, handlers);
bindControls();
bindMapInteractions();
bindKeyboard();
bindStore();
await refreshState({ fit: true });
startRuntimePolling();

function bindControls() {
  $("refresh").addEventListener("click", () => refreshState());
  $("fit").addEventListener("click", fitCurrentWorld);
  $("locate").addEventListener("click", locateCoordinates);
  $("toggle-admin").addEventListener("click", toggleAdminMode);
  $("world-select").addEventListener("change", () => {
    store.setState({ world: $("world-select").value, selectedComponents: new Set(), focused: { type: "", id: "" } }, "world");
    fitCurrentWorld();
  });
  for (const button of document.querySelectorAll("[data-tab]")) {
    button.addEventListener("click", () => changeTab(button.dataset.tab));
  }
  for (const button of document.querySelectorAll("[data-tool]")) {
    button.addEventListener("click", () => setTool(button.dataset.tool));
  }

  $("route-search").addEventListener("input", () => updateFilter("routeQuery", $("route-search").value));
  $("route-filter").addEventListener("change", () => updateFilter("routeKind", $("route-filter").value));
  $("station-search").addEventListener("input", () => updateFilter("stationQuery", $("station-search").value));
  $("mask-search").addEventListener("input", () => updateFilter("maskQuery", $("mask-search").value));

  bindDraftForm("route-editor", "route", {
    lineWidth: (value) => Number(value),
    autoMatch: (_, element) => element.checked,
  });
  bindDraftForm("station-editor", "station", numericBounds());
  bindDraftForm("mask-editor", "mask", { ...numericBounds(), enabled: (_, element) => element.checked });
  $("route-editor").addEventListener("submit", saveRoute);
  $("station-editor").addEventListener("submit", saveStation);
  $("mask-editor").addEventListener("submit", saveMask);
  $("reset-route").addEventListener("click", resetRouteDraft);
  $("reset-station").addEventListener("click", resetStationDraft);
  $("reset-mask").addEventListener("click", resetMaskDraft);
  $("new-station").addEventListener("click", resetStationDraft);
  $("new-mask").addEventListener("click", resetMaskDraft);

  $("assign-selection").addEventListener("click", assignSelectionToDraft);
  $("hide-selection").addEventListener("click", hideSelection);
  $("unassign-selection").addEventListener("click", unassignSelection);
  $("clear-selection").addEventListener("click", clearSelection);
  $("rescan").addEventListener("click", requestRescan);

  bindLayerToggle("layer-background", "background");
  bindLayerToggle("layer-grid", "grid");
  bindLayerToggle("layer-unclassified", "unclassified");
  bindLayerToggle("layer-stations", "stations");
  bindLayerToggle("layer-masks", "masks");
  bindLayerRange("background-opacity", "backgroundOpacity", "background-opacity-output");
  bindLayerRange("background-wash", "backgroundWash", "background-wash-output");
  $("auto-load-route").checked = store.getState().preferences.autoLoadRoute;
  $("snap-grid").checked = store.getState().preferences.snapGrid;
  $("auto-load-route").addEventListener("change", () => updatePreference("autoLoadRoute", $("auto-load-route").checked));
  $("snap-grid").addEventListener("change", () => updatePreference("snapGrid", $("snap-grid").checked));

  window.addEventListener("beforeunload", (event) => {
    if (Object.values(store.getState().dirty).some(Boolean)) {
      event.preventDefault();
    }
  });
}

function bindMapInteractions() {
  const stage = $("map-stage");
  stage.addEventListener("wheel", (event) => {
    event.preventDefault();
    const point = map.worldPoint(event);
    store.setState({ view: zoomView(store.getState().view, point, event.deltaY < 0 ? 0.82 : 1.22) }, "view");
  }, { passive: false });

  stage.addEventListener("pointerdown", (event) => {
    const objectTarget = event.target.closest?.("[data-object-type]");
    if (objectTarget) {
      focusOverlayObject(objectTarget.dataset.objectType, objectTarget.dataset.objectId);
      return;
    }
    const state = store.getState();
    const point = map.worldPoint(event);
    stage.setPointerCapture(event.pointerId);
    if (state.adminMode && ["route-box", "station", "mask"].includes(state.toolMode)) {
      store.setState({
        dragging: { pointerId: event.pointerId, mode: state.toolMode, start: point, current: point, append: event.shiftKey },
        draftBox: { mode: state.toolMode, box: normalizeBox(point, point, state.preferences.snapGrid) },
      }, "draft");
      return;
    }
    if (state.adminMode && state.toolMode === "select") {
      const line = map.nearestLine(point);
      if (line) {
        selectComponent(line.componentId, event.shiftKey);
      } else if (!event.shiftKey) {
        clearSelection();
      }
      return;
    }
    store.setState({
      panning: {
        pointerId: event.pointerId,
        clientX: event.clientX,
        clientY: event.clientY,
        view: { ...state.view },
        moved: false,
      },
    }, "pan-start");
    stage.classList.add("is-panning");
  });

  stage.addEventListener("pointermove", (event) => {
    const point = map.worldPoint(event);
    $("coords").textContent = `X ${Math.floor(point.x)} · Z ${Math.floor(point.z)}`;
    const state = store.getState();
    if (state.dragging?.pointerId === event.pointerId) {
      const dragging = { ...state.dragging, current: point };
      store.setState({
        dragging,
        draftBox: { mode: dragging.mode, box: normalizeBox(dragging.start, point, state.preferences.snapGrid) },
      }, "draft");
      return;
    }
    if (state.panning?.pointerId === event.pointerId) {
      const dx = ((state.panning.clientX - event.clientX) / Math.max(1, map.width)) * state.panning.view.w;
      const dz = ((state.panning.clientY - event.clientY) / Math.max(1, map.height)) * state.panning.view.h;
      store.setState({
        view: panView(state.panning.view, dx, dz),
        panning: { ...state.panning, moved: state.panning.moved || Math.abs(dx) + Math.abs(dz) > state.view.w / map.width * 3 },
      }, "view");
    }
  });

  stage.addEventListener("pointerup", (event) => {
    const state = store.getState();
    if (state.dragging?.pointerId === event.pointerId) {
      applyBoxAction(state.dragging, state.draftBox?.box);
    } else if (state.panning?.pointerId === event.pointerId && !state.panning.moved) {
      const line = map.nearestLine(map.worldPoint(event));
      if (line) {
        focusLine(line);
      }
    }
    store.setState({ dragging: null, panning: null, draftBox: null }, "draft");
    stage.classList.remove("is-panning");
    if (stage.hasPointerCapture(event.pointerId)) {
      stage.releasePointerCapture(event.pointerId);
    }
  });

  stage.addEventListener("pointercancel", () => {
    store.setState({ dragging: null, panning: null, draftBox: null }, "draft");
    stage.classList.remove("is-panning");
  });
  stage.addEventListener("pointerleave", () => {
    if (!store.getState().panning && !store.getState().dragging) {
      $("coords").textContent = "X -- · Z --";
    }
  });
}

function bindKeyboard() {
  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      store.setState({ dragging: null, panning: null, draftBox: null }, "draft");
      setTool("pan");
      return;
    }
    if (event.key === "Delete" && store.getState().adminMode && !isTypingTarget(event.target)) {
      event.preventDefault();
      hideSelection();
    }
  });
}

function bindStore() {
  store.subscribe((state, reason) => {
    if (reason === "view") {
      map.renderView();
      renderZoomLabel();
      return;
    }
    if (reason === "selection" || reason === "draft") {
      map.renderSelection();
      ui.renderSelectionBar();
      if (reason === "selection") {
        ui.renderEditors();
      }
      return;
    }
    if (reason === "layers") {
      map.renderLayers();
      ui.renderLayers();
      return;
    }
    if (reason === "runtime") {
      ui.renderRuntime();
      return;
    }
    if (reason === "filter") {
      ui.renderLists();
      return;
    }
    if (reason === "draft-input") {
      ui.renderEditors();
      return;
    }
    if (reason === "tab") {
      ui.renderTabs();
      return;
    }
    if (reason === "focus") {
      ui.renderLists();
      map.drawOverlay();
      return;
    }
    if (reason === "world" || reason === "mode") {
      ui.renderAll();
      map.renderAll();
    }
  });
}

async function refreshState({ fit = false, silent = false } = {}) {
  if (!silent) {
    ui.setMapMessage("正在加载数据");
  }
  store.setState({ loading: true }, "loading");
  try {
    const data = await api.state();
    const current = store.getState();
    const worlds = collectWorlds(data);
    const world = worlds.has(current.world)
      ? current.world
      : data.background?.world || worlds.values().next().value || "world";
    const validComponents = new Set((data.components || []).map((component) => component.id));
    const selectedComponents = new Set(Array.from(current.selectedComponents).filter((id) => validComponents.has(id)));
    const runtime = data.runtime || current.runtime || { phase: "ready" };
    const routeLayers = { ...current.layers.routes };
    for (const route of data.routes || []) {
      if (!(route.id in routeLayers)) {
        routeLayers[route.id] = true;
      }
    }
    store.setState({
      data,
      runtime,
      adminMode: Boolean(data.admin && current.token),
      world,
      selectedComponents,
      layers: { ...current.layers, routes: routeLayers },
      loading: false,
      lastLoadedAt: Date.now(),
      lastSeenRenderAt: Math.max(current.lastSeenRenderAt, Number(runtime.lastRenderCompletedAt) || 0),
    }, "data");
    map.setData(data);
    ui.renderAll();
    if (fit || !hasInitialFit) {
      fitCurrentWorld();
      hasInitialFit = true;
    }
    ui.setMapMessage(`线路 ${data.routes?.length || 0} · component ${data.components?.length || 0} · 站点 ${data.stations?.length || 0}`);
  } catch (error) {
    store.setState({ loading: false }, "loading");
    ui.setMapMessage(error.message || "加载失败");
    if (!silent) {
      ui.toast(error.message || "加载失败", "error", 5000);
    }
  }
}

async function toggleAdminMode() {
  const state = store.getState();
  if (state.adminMode) {
    store.setState({ adminMode: false, token: "", selectedComponents: new Set(), activeTab: "routes", toolMode: "pan" }, "mode");
    await refreshState({ silent: true });
    ui.toast("已切换到浏览模式");
    return;
  }
  let candidate = rememberedToken;
  while (true) {
    const token = await ui.requestToken(candidate);
    if (token === null) {
      return;
    }
    try {
      const result = await api.authCheck(token);
      if (!result.admin) {
        candidate = token;
        ui.toast("管理密钥无效", "error");
        continue;
      }
      localStorage.setItem("bluemaprailway-token", token);
      store.setState({ token, adminMode: true }, "mode");
      await refreshState({ silent: true });
      ui.toast("已进入管理模式");
      return;
    } catch (error) {
      candidate = token;
      ui.toast(error.message || "验证失败", "error");
    }
  }
}

function changeTab(tab) {
  if (tab === "rules" && !store.getState().adminMode) {
    return;
  }
  store.setState({ activeTab: tab }, "tab");
}

function setTool(tool) {
  const state = store.getState();
  const next = state.adminMode || ["pan"].includes(tool) ? tool : "pan";
  store.setState({ toolMode: next, dragging: null, draftBox: null }, "draft");
  $("map-stage").dataset.tool = next;
  for (const button of document.querySelectorAll("[data-tool]")) {
    button.classList.toggle("active", button.dataset.tool === next);
  }
}

function fitCurrentWorld() {
  const state = store.getState();
  const bounds = worldBounds(state.data, state.world);
  store.setState({ view: fitView(bounds, map.width, map.height, 32) }, "view");
}

function locateCoordinates() {
  const x = Number($("coord-x-input").value);
  const z = Number($("coord-z-input").value);
  if (!Number.isFinite(x) || !Number.isFinite(z)) {
    ui.toast("请输入有效的 X/Z 坐标", "error");
    return;
  }
  store.setState({ view: centerView(store.getState().view, x, z) }, "view");
}

function applyBoxAction(dragging, box) {
  if (!box) {
    return;
  }
  const state = store.getState();
  if (dragging.mode === "route-box") {
    const matches = map.componentsInBox(box);
    const selection = dragging.append ? new Set(state.selectedComponents) : new Set();
    for (const componentId of matches) {
      selection.add(componentId);
    }
    store.setState({
      selectedComponents: selection,
      routeDraft: { ...state.routeDraft, componentIds: Array.from(selection) },
      dirty: { ...state.dirty, route: true },
      activeTab: "routes",
    }, "selection");
    ui.renderTabs();
    return;
  }
  if (dragging.mode === "station") {
    store.setState({
      stationDraft: { ...state.stationDraft, world: state.world, ...box },
      dirty: { ...state.dirty, station: true },
      activeTab: "stations",
      focused: { type: "station-draft", id: "" },
    }, "draft-input");
    changeTab("stations");
    ui.toast("站点范围已更新", "pending");
    return;
  }
  if (dragging.mode === "mask") {
    store.setState({
      maskDraft: { ...state.maskDraft, world: state.world, ...box },
      dirty: { ...state.dirty, mask: true },
      activeTab: "rules",
      focused: { type: "mask-draft", id: "" },
      layers: { ...state.layers, masks: true },
    }, "draft-input");
    changeTab("rules");
    map.renderLayers();
    ui.toast("裁切范围已更新", "pending");
  }
}

function selectComponent(componentId, append) {
  const state = store.getState();
  const component = state.data?.components?.find((item) => item.id === componentId);
  if (component?.routeId && state.preferences.autoLoadRoute && !append) {
    const route = state.data.routes.find((item) => item.id === component.routeId);
    if (route) {
      editRoute(route);
      return;
    }
  }
  const selectedComponents = selectionWithToggle(state.selectedComponents, componentId, append || state.selectedComponents.size > 0);
  store.setState({
    selectedComponents,
    focused: { type: "component", id: componentId },
    routeDraft: { ...state.routeDraft, componentIds: Array.from(selectedComponents) },
    dirty: { ...state.dirty, route: true },
    activeTab: "routes",
  }, "selection");
  ui.renderTabs();
  focusComponent(component);
}

function clearSelection() {
  const state = store.getState();
  store.setState({
    selectedComponents: new Set(),
    routeDraft: { ...state.routeDraft, componentIds: [] },
    dirty: { ...state.dirty, route: Boolean(state.routeDraft.id) },
  }, "selection");
}

function assignSelectionToDraft() {
  const state = store.getState();
  store.setState({
    activeTab: "routes",
    routeDraft: { ...state.routeDraft, componentIds: Array.from(state.selectedComponents) },
    dirty: { ...state.dirty, route: true },
  }, "draft-input");
  changeTab("routes");
}

async function hideSelection() {
  const state = store.getState();
  if (state.selectedComponents.size === 0) {
    return;
  }
  if (!await ui.confirm("隐藏铁路段", `隐藏选中的 ${state.selectedComponents.size} 个 component？再次扫描后仍会保持隐藏。`)) {
    return;
  }
  await runMutation("正在保存隐藏规则", async () => {
    await api.saveHiddenLine({
      id: `hide-components-${Date.now()}`,
      name: `隐藏 ${state.selectedComponents.size} 个 component`,
      enabled: true,
      routeIds: [],
      componentIds: Array.from(state.selectedComponents),
    });
    clearSelection();
  }, "线路已隐藏");
}

async function unassignSelection() {
  const state = store.getState();
  const selected = state.selectedComponents;
  const affected = (state.data?.routes || []).filter((route) => route.componentIds.some((id) => selected.has(id)));
  if (affected.length === 0) {
    ui.toast("选中 component 尚未归类", "pending");
    return;
  }
  if (!await ui.confirm("取消线路归类", `从 ${affected.length} 条线路中移除选中的 component？`)) {
    return;
  }
  await runMutation("正在取消归类", async () => {
    for (const route of affected) {
      await api.saveRoute({ ...route, componentIds: route.componentIds.filter((id) => !selected.has(id)) });
    }
    clearSelection();
  }, "已取消线路归类");
}

async function saveRoute(event) {
  event.preventDefault();
  const draft = store.getState().routeDraft;
  if (!draft.id?.match(/^[A-Za-z0-9_-]+$/)) {
    ui.toast("线路 ID 只能包含字母、数字、下划线和短横线", "error");
    return;
  }
  await runMutation("正在保存线路", () => api.saveRoute({
    id: draft.id.trim(),
    name: draft.name.trim() || draft.id.trim(),
    color: draft.color,
    lineWidth: Number(draft.lineWidth) || 3,
    autoMatch: Boolean(draft.autoMatch),
    componentIds: Array.from(new Set(draft.componentIds || [])),
  }), "线路已保存，扫描已排队", () => {
    const state = store.getState();
    store.setState({ dirty: { ...state.dirty, route: false } }, "draft-input");
  });
}

async function saveStation(event) {
  event.preventDefault();
  const draft = store.getState().stationDraft;
  if (!draft.id?.match(/^[A-Za-z0-9_-]+$/)) {
    ui.toast("站点 ID 只能包含字母、数字、下划线和短横线", "error");
    return;
  }
  await runMutation("正在保存站点", () => api.saveStation(normalizedAreaDraft(draft)), "站点已保存，扫描已排队", () => {
    const state = store.getState();
    store.setState({ dirty: { ...state.dirty, station: false } }, "draft-input");
  });
}

async function saveMask(event) {
  event.preventDefault();
  const draft = store.getState().maskDraft;
  if (!draft.id?.match(/^[A-Za-z0-9_-]+$/)) {
    ui.toast("裁切规则 ID 只能包含字母、数字、下划线和短横线", "error");
    return;
  }
  await runMutation("正在保存裁切规则", () => api.saveMask({ ...normalizedAreaDraft(draft), enabled: Boolean(draft.enabled) }), "裁切规则已保存，扫描已排队", () => {
    const state = store.getState();
    store.setState({ dirty: { ...state.dirty, mask: false } }, "draft-input");
  });
}

function resetRouteDraft() {
  const state = store.getState();
  store.setState({ routeDraft: blankRouteDraft(state.world), selectedComponents: new Set(), dirty: { ...state.dirty, route: false } }, "draft-input");
  map.renderSelection();
  ui.renderSelectionBar();
}

function resetStationDraft() {
  const state = store.getState();
  store.setState({ stationDraft: blankStationDraft(state.world), dirty: { ...state.dirty, station: false }, focused: { type: "", id: "" } }, "draft-input");
}

function resetMaskDraft() {
  const state = store.getState();
  store.setState({ maskDraft: blankMaskDraft(state.world), dirty: { ...state.dirty, mask: false }, focused: { type: "", id: "" } }, "draft-input");
}

function focusRoute(route) {
  const text = `${route.name || route.id}\nID: ${route.id}\n绑定 component: ${route.componentIds.length}\n自动延续: ${route.autoMatch ? "开启" : "关闭"}`;
  store.setState({ focused: { type: "route", id: route.id } }, "focus");
  ui.setRouteInspector(text);
  ui.setInspector(text);
}

function editRoute(route) {
  const state = store.getState();
  const selection = new Set(route.componentIds || []);
  store.setState({
    routeDraft: { ...route, color: route.color || "#ef4444", lineWidth: route.lineWidth > 0 ? route.lineWidth : 3 },
    selectedComponents: selection,
    focused: { type: "route", id: route.id },
    dirty: { ...state.dirty, route: false },
    activeTab: "routes",
  }, "selection");
  ui.renderEditors();
  ui.renderLists();
}

function locateRoute(route) {
  const lines = (store.getState().data?.lines || []).filter((line) => line.routeId === route.id);
  focusLines(lines);
  focusRoute(route);
}

async function hideRoute(route) {
  if (!await ui.confirm("隐藏整条线路", `隐藏线路“${route.name || route.id}”？再次扫描后仍会保持隐藏。`)) {
    return;
  }
  await runMutation("正在保存隐藏规则", () => api.saveHiddenLine({
    id: `hide-route-${route.id}`,
    name: `隐藏线路: ${route.name || route.id}`,
    enabled: true,
    routeIds: [route.id],
    componentIds: [],
  }), "线路已隐藏");
}

async function deleteRoute(route) {
  if (!await ui.confirm("删除线路", `删除线路“${route.name || route.id}”？铁轨不会被删除，只会失去该线路分类。`)) {
    return;
  }
  await runMutation("正在删除线路", () => api.deleteRoute(route.id), "线路已删除", resetRouteDraft);
}

function focusComponent(component) {
  if (!component) {
    return;
  }
  const text = `${component.routeName || "未分类线路"}\ncomponent: ${component.id}\n点数: ${component.pointCount}\n长度: ${Math.round(Number(component.length) || 0)} 格`;
  store.setState({ focused: { type: "component", id: component.id } }, "focus");
  ui.setRouteInspector(text);
  ui.setInspector(text);
}

function locateComponent(component) {
  if (!component) {
    return;
  }
  focusBounds({ minX: component.minX, minZ: component.minZ, maxX: component.maxX, maxZ: component.maxZ });
  focusComponent(component);
}

function focusStation(station) {
  const routeNames = routesPassingStation(station);
  const text = `${station.name || station.id}\n世界: ${station.world}\n范围: ${station.minX},${station.minY},${station.minZ} → ${station.maxX},${station.maxY},${station.maxZ}\n经过线路: ${routeNames.join("、") || "无"}`;
  store.setState({ focused: { type: "station", id: station.id } }, "focus");
  ui.setStationInspector(text);
  ui.setInspector(text);
}

function editStation(station) {
  const state = store.getState();
  store.setState({ stationDraft: { ...station }, dirty: { ...state.dirty, station: false }, activeTab: "stations", focused: { type: "station", id: station.id } }, "draft-input");
  focusStation(station);
}

function locateStation(station) {
  focusBounds(station);
  focusStation(station);
}

async function deleteStation(station) {
  if (!await ui.confirm("删除站点", `删除站点“${station.name || station.id}”？`)) {
    return;
  }
  await runMutation("正在删除站点", () => api.deleteStation(station.id), "站点已删除", resetStationDraft);
}

function focusMask(mask) {
  const text = `${mask.name || mask.id}\n世界: ${mask.world}\n状态: ${mask.enabled ? "启用" : "停用"}\n范围: ${mask.minX},${mask.minY},${mask.minZ} → ${mask.maxX},${mask.maxY},${mask.maxZ}`;
  store.setState({ focused: { type: "mask", id: mask.id } }, "focus");
  ui.setInspector(text);
}

function editMask(mask) {
  const state = store.getState();
  store.setState({ maskDraft: { ...mask }, dirty: { ...state.dirty, mask: false }, activeTab: "rules", focused: { type: "mask", id: mask.id } }, "draft-input");
  focusMask(mask);
}

function locateMask(mask) {
  focusBounds(mask);
  focusMask(mask);
}

async function deleteMask(mask) {
  if (!await ui.confirm("删除裁切规则", `删除裁切规则“${mask.name || mask.id}”？被裁切的线路将在后续扫描中重新显示。`)) {
    return;
  }
  await runMutation("正在删除裁切规则", () => api.deleteMask(mask.id), "裁切规则已删除", resetMaskDraft);
}

function focusHiddenLine(rule) {
  const text = `${rule.name || rule.id}\n状态: ${rule.enabled ? "启用" : "停用"}\nroute: ${rule.routeIds.join(", ") || "无"}\ncomponent: ${rule.componentIds.map(shortComponentId).join(", ") || "无"}`;
  store.setState({ focused: { type: "hidden", id: rule.id } }, "focus");
  ui.setInspector(text);
  const lines = (store.getState().data?.lines || []).filter((line) => rule.routeIds.includes(line.routeId) || rule.componentIds.includes(line.componentId));
  if (lines.length > 0) {
    focusLines(lines);
  }
}

async function deleteHiddenLine(rule) {
  if (!await ui.confirm("删除隐藏规则", `删除隐藏规则“${rule.name || rule.id}”？相应线路将在后续扫描中重新显示。`)) {
    return;
  }
  await runMutation("正在删除隐藏规则", () => api.deleteHiddenLine(rule.id), "隐藏规则已删除");
}

function focusLine(line) {
  const component = store.getState().data?.components?.find((item) => item.id === line.componentId);
  if (component) {
    focusComponent(component);
  }
}

function focusOverlayObject(type, id) {
  const state = store.getState();
  if (type === "station") {
    const station = state.data?.stations?.find((item) => item.id === id);
    if (station) focusStation(station);
  } else if (type === "mask") {
    const mask = state.data?.masks?.find((item) => item.id === id);
    if (mask) focusMask(mask);
  }
}

function focusLines(lines) {
  const bounds = combineLineBounds(lines);
  if (bounds) {
    focusBounds(bounds);
  }
}

function focusBounds(bounds) {
  store.setState({ view: fitView(bounds, map.width, map.height, 56) }, "view");
}

function toggleRouteLayer(routeId, visible) {
  const state = store.getState();
  updateLayers({ routes: { ...state.layers.routes, [routeId]: visible } });
}

function bindLayerToggle(id, key) {
  $(id).addEventListener("change", () => updateLayers({ [key]: $(id).checked }));
}

function bindLayerRange(id, key) {
  $(id).addEventListener("input", () => updateLayers({ [key]: Number($(id).value) }));
}

function updateLayers(patch) {
  const state = store.getState();
  const layers = { ...state.layers, ...patch };
  localStorage.setItem("bluemaprailway-layers", JSON.stringify(layers));
  store.setState({ layers }, "layers");
}

function updatePreference(key, value) {
  const state = store.getState();
  const preferences = { ...state.preferences, [key]: value };
  localStorage.setItem("bluemaprailway-edit-preferences", JSON.stringify(preferences));
  store.setState({ preferences }, "preferences");
}

function updateFilter(key, value) {
  const state = store.getState();
  store.setState({ filters: { ...state.filters, [key]: value } }, "filter");
}

function bindDraftForm(formId, type, converters) {
  const form = $(formId);
  form.addEventListener("input", (event) => {
    const element = event.target;
    if (!element.name) {
      return;
    }
    const state = store.getState();
    const key = `${type}Draft`;
    const converter = converters[element.name];
    const value = converter ? converter(element.value, element) : element.value;
    store.setState({
      [key]: { ...state[key], [element.name]: value },
      ...setDirty(state, type, true),
    }, "draft-input");
  });
}

function numericBounds() {
  const converters = {};
  for (const key of ["minX", "minY", "minZ", "maxX", "maxY", "maxZ"]) {
    converters[key] = (value) => value === "" ? "" : Number(value);
  }
  return converters;
}

async function requestRescan() {
  if (!await ui.confirm("全量重扫", "全量重扫可能持续较长时间。确认将任务加入扫描队列？")) {
    return;
  }
  try {
    await api.rescan();
    ui.toast("全量重扫已排队", "pending");
    await pollRuntime();
  } catch (error) {
    ui.toast(error.message || "重扫请求失败", "error");
  }
}

async function runMutation(pendingMessage, operation, successMessage, afterSuccess) {
  ui.toast(pendingMessage, "pending", 1800);
  try {
    await operation();
    afterSuccess?.();
    await refreshState({ silent: true });
    ui.toast(successMessage, "success");
  } catch (error) {
    ui.toast(error.message || "操作失败", "error", 5000);
  }
}

function startRuntimePolling() {
  window.clearInterval(runtimeTimer);
  runtimeTimer = window.setInterval(pollRuntime, 3000);
}

async function pollRuntime() {
  try {
    const runtime = await api.runtime();
    const state = store.getState();
    const completedAt = Number(runtime.lastRenderCompletedAt) || 0;
    store.setState({ runtime }, "runtime");
    if (completedAt > state.lastSeenRenderAt) {
      store.setState({ lastSeenRenderAt: completedAt }, "runtime");
      await refreshState({ silent: true });
    }
  } catch {
    // Runtime polling is optional during mixed-version upgrades.
  }
}

function renderZoomLabel() {
  const view = store.getState().view;
  const pixelsPerBlock = map.width / Math.max(0.001, view.w);
  $("zoom-label").textContent = `${Math.round(pixelsPerBlock * 100)}%`;
}

function routesPassingStation(station) {
  const names = new Set();
  const box = { minX: station.minX, minZ: station.minZ, maxX: station.maxX, maxZ: station.maxZ };
  for (const line of map.index.queryBox(box)) {
    if (line.world === station.world && line.routeName) {
      names.add(line.routeName);
    }
  }
  return Array.from(names).sort();
}

function normalizedAreaDraft(draft) {
  const minX = Number(draft.minX);
  const minY = Number(draft.minY);
  const minZ = Number(draft.minZ);
  const maxX = Number(draft.maxX);
  const maxY = Number(draft.maxY);
  const maxZ = Number(draft.maxZ);
  return {
    ...draft,
    name: draft.name?.trim() || draft.id?.trim(),
    world: draft.world || store.getState().world,
    minX: Math.min(minX, maxX),
    minY: Math.min(minY, maxY),
    minZ: Math.min(minZ, maxZ),
    maxX: Math.max(minX, maxX),
    maxY: Math.max(minY, maxY),
    maxZ: Math.max(minZ, maxZ),
  };
}

function collectWorlds(data) {
  const worlds = new Set();
  if (data?.background?.world) worlds.add(data.background.world);
  for (const collection of [data?.lines, data?.stations, data?.components]) {
    for (const item of collection || []) {
      if (item.world) worlds.add(item.world);
    }
  }
  return worlds;
}

function worldBounds(data, world) {
  const boxes = [];
  for (const line of data?.lines || []) {
    if (line.world === world) {
      const bounds = lineBounds(line.points);
      if (bounds) boxes.push(bounds);
    }
  }
  for (const station of data?.stations || []) {
    if (station.world === world) boxes.push(station);
  }
  for (const mask of data?.masks || []) {
    if (mask.world === world) boxes.push(mask);
  }
  if (boxes.length === 0 && data?.background?.world === world && data.bounds) {
    boxes.push(data.bounds);
  }
  return combineBounds(boxes) || { minX: -128, minZ: -128, maxX: 128, maxZ: 128 };
}

function combineLineBounds(lines) {
  return combineBounds(lines.map((line) => lineBounds(line.points)).filter(Boolean));
}

function combineBounds(boxes) {
  if (!boxes.length) return null;
  return {
    minX: Math.min(...boxes.map((box) => Number(box.minX))),
    minZ: Math.min(...boxes.map((box) => Number(box.minZ))),
    maxX: Math.max(...boxes.map((box) => Number(box.maxX))),
    maxZ: Math.max(...boxes.map((box) => Number(box.maxZ))),
  };
}

function isTypingTarget(target) {
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement;
}

function readJsonSetting(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key) || "") || fallback;
  } catch {
    return fallback;
  }
}
