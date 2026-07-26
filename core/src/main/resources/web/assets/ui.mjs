const $ = (id) => document.getElementById(id);

export class DashboardUI {
  constructor(store, handlers) {
    this.store = store;
    this.handlers = handlers;
    this.confirmResolver = null;
  }

  renderAll() {
    this.renderMode();
    this.renderWorlds();
    this.renderTabs();
    this.renderLists();
    this.renderEditors();
    this.renderLayers();
    this.renderRuntime();
    this.renderSelectionBar();
  }

  renderMode() {
    const state = this.store.getState();
    document.body.classList.toggle("admin-mode", state.adminMode);
    $("toggle-admin").textContent = state.adminMode ? "退出管理" : "进入管理";
    if (!state.adminMode && state.activeTab === "rules") {
      this.handlers.changeTab("routes");
    }
  }

  renderWorlds() {
    const state = this.store.getState();
    const worlds = new Set();
    if (state.data?.background?.world) {
      worlds.add(state.data.background.world);
    }
    for (const collection of [state.data?.lines, state.data?.stations, state.data?.components]) {
      for (const item of collection || []) {
        if (item.world) {
          worlds.add(item.world);
        }
      }
    }
    const select = $("world-select");
    select.replaceChildren();
    for (const world of Array.from(worlds).sort()) {
      const option = document.createElement("option");
      option.value = world;
      option.textContent = world;
      option.selected = world === state.world;
      select.append(option);
    }
  }

  renderTabs() {
    const state = this.store.getState();
    for (const button of document.querySelectorAll("[data-tab]")) {
      const active = button.dataset.tab === state.activeTab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", String(active));
    }
    for (const panel of document.querySelectorAll("[data-panel]")) {
      panel.classList.toggle("active", panel.dataset.panel === state.activeTab);
    }
  }

  renderLists() {
    this.renderRoutes();
    this.renderStations();
    this.renderMasks();
    this.renderHiddenLines();
  }

  renderRoutes() {
    const state = this.store.getState();
    const list = $("route-list");
    list.replaceChildren();
    const query = state.filters.routeQuery.toLocaleLowerCase();
    const kind = state.filters.routeKind;
    const routes = (state.data?.routes || []).filter((route) => {
      if (kind === "unclassified") {
        return false;
      }
      return `${route.name} ${route.id}`.toLocaleLowerCase().includes(query);
    });
    const unclassified = (state.data?.components || []).filter((component) => {
      if (component.routeId || kind === "classified") {
        return false;
      }
      return `${component.id} 未分类`.toLocaleLowerCase().includes(query);
    });

    for (const route of routes) {
      const row = this.objectRow({
        title: route.name || route.id,
        meta: `${route.id} · ${route.componentIds.length} component`,
        color: route.color || "#ef4444",
        focused: state.focused.type === "route" && state.focused.id === route.id,
        onOpen: () => this.handlers.focusRoute(route),
        actions: [
          ["定位", () => this.handlers.locateRoute(route)],
          ...(state.adminMode ? [
            ["编辑", () => this.handlers.editRoute(route)],
            ["隐藏", () => this.handlers.hideRoute(route)],
            ["删除", () => this.handlers.deleteRoute(route), "danger"],
          ] : []),
        ],
      });
      list.append(row);
    }

    for (const component of unclassified) {
      const shortId = shortComponentId(component.id);
      list.append(this.objectRow({
        title: `未分类 ${shortId}`,
        meta: `${component.pointCount} 点 · ${Math.round(Number(component.length) || 0)} 格`,
        color: "#f59e0b",
        focused: state.focused.type === "component" && state.focused.id === component.id,
        onOpen: () => this.handlers.focusComponent(component),
        actions: [["定位", () => this.handlers.locateComponent(component)]],
      }));
    }
    if (list.childElementCount === 0) {
      list.append(this.emptyState("没有匹配的线路"));
    }
  }

  renderStations() {
    const state = this.store.getState();
    const query = state.filters.stationQuery.toLocaleLowerCase();
    const list = $("station-list");
    list.replaceChildren();
    for (const station of state.data?.stations || []) {
      if (station.world !== state.world || !`${station.name} ${station.id}`.toLocaleLowerCase().includes(query)) {
        continue;
      }
      list.append(this.objectRow({
        title: station.name || station.id,
        meta: `${station.id} · ${station.minX},${station.minZ} → ${station.maxX},${station.maxZ}`,
        focused: state.focused.type === "station" && state.focused.id === station.id,
        onOpen: () => this.handlers.focusStation(station),
        actions: [
          ["定位", () => this.handlers.locateStation(station)],
          ...(state.adminMode ? [
            ["编辑", () => this.handlers.editStation(station)],
            ["删除", () => this.handlers.deleteStation(station), "danger"],
          ] : []),
        ],
      }));
    }
    if (list.childElementCount === 0) {
      list.append(this.emptyState("当前世界没有站点"));
    }
  }

  renderMasks() {
    const state = this.store.getState();
    const query = state.filters.maskQuery.toLocaleLowerCase();
    const list = $("mask-list");
    list.replaceChildren();
    for (const mask of state.data?.masks || []) {
      if (mask.world !== state.world || !`${mask.name} ${mask.id}`.toLocaleLowerCase().includes(query)) {
        continue;
      }
      list.append(this.objectRow({
        title: mask.name || mask.id,
        meta: `${mask.id} · ${mask.enabled ? "启用" : "停用"}`,
        color: mask.enabled ? "#d16b16" : "#718096",
        focused: state.focused.type === "mask" && state.focused.id === mask.id,
        onOpen: () => this.handlers.focusMask(mask),
        actions: [
          ["定位", () => this.handlers.locateMask(mask)],
          ["编辑", () => this.handlers.editMask(mask)],
          ["删除", () => this.handlers.deleteMask(mask), "danger"],
        ],
      }));
    }
    if (list.childElementCount === 0) {
      list.append(this.emptyState("没有裁切规则"));
    }
  }

  renderHiddenLines() {
    const state = this.store.getState();
    const list = $("hidden-list");
    list.replaceChildren();
    $("hidden-count").textContent = String(state.data?.hiddenLines?.length || 0);
    for (const rule of state.data?.hiddenLines || []) {
      list.append(this.objectRow({
        title: rule.name || rule.id,
        meta: `${rule.routeIds.length} route · ${rule.componentIds.length} component`,
        color: rule.enabled ? "#c43b3b" : "#718096",
        onOpen: () => this.handlers.focusHiddenLine(rule),
        actions: [
          ["查看", () => this.handlers.focusHiddenLine(rule)],
          ["删除", () => this.handlers.deleteHiddenLine(rule), "danger"],
        ],
      }));
    }
    if (list.childElementCount === 0) {
      list.append(this.emptyState("没有隐藏规则"));
    }
  }

  objectRow({ title, meta, color, focused, onOpen, actions }) {
    const row = document.createElement("div");
    row.className = `object-row${focused ? " focused" : ""}`;
    const main = document.createElement("div");
    main.className = "object-main";
    main.tabIndex = 0;
    const titleElement = document.createElement("div");
    titleElement.className = "object-title";
    if (color) {
      const swatch = document.createElement("span");
      swatch.className = "color-swatch";
      swatch.style.backgroundColor = color;
      titleElement.append(swatch);
    }
    const text = document.createElement("span");
    text.textContent = title;
    titleElement.append(text);
    const metaElement = document.createElement("div");
    metaElement.className = "object-meta";
    metaElement.textContent = meta;
    main.append(titleElement, metaElement);
    main.addEventListener("click", onOpen);
    main.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        onOpen();
      }
    });
    const actionContainer = document.createElement("div");
    actionContainer.className = "object-actions";
    for (const [label, action, className] of actions || []) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = label;
      button.title = label;
      button.setAttribute("aria-label", label);
      if (className) {
        button.className = className;
      }
      button.addEventListener("click", (event) => {
        event.stopPropagation();
        action();
      });
      actionContainer.append(button);
    }
    row.append(main, actionContainer);
    return row;
  }

  emptyState(message) {
    const element = document.createElement("div");
    element.className = "empty-state";
    element.textContent = message;
    return element;
  }

  renderEditors() {
    const state = this.store.getState();
    this.writeDraft("route", state.routeDraft);
    this.writeDraft("station", state.stationDraft);
    this.writeDraft("mask", state.maskDraft);
    $("route-dirty").hidden = !state.dirty.route;
    $("station-dirty").hidden = !state.dirty.station;
    $("mask-dirty").hidden = !state.dirty.mask;
    $("route-component-count").textContent = String(state.routeDraft.componentIds?.length || 0);
    const chips = $("selected-components");
    chips.replaceChildren();
    for (const componentId of state.routeDraft.componentIds || []) {
      const chip = document.createElement("span");
      chip.className = "component-chip";
      chip.textContent = shortComponentId(componentId);
      chip.title = componentId;
      chips.append(chip);
    }
  }

  writeDraft(type, draft) {
    const form = $(`${type}-editor`);
    if (!form || !draft) {
      return;
    }
    for (const element of form.elements) {
      if (!element.name || !(element.name in draft)) {
        continue;
      }
      if (element.type === "checkbox") {
        element.checked = Boolean(draft[element.name]);
      } else if (document.activeElement !== element) {
        element.value = draft[element.name] ?? "";
      }
    }
  }

  renderLayers() {
    const state = this.store.getState();
    $("layer-background").checked = state.layers.background;
    $("layer-grid").checked = state.layers.grid;
    $("layer-unclassified").checked = state.layers.unclassified;
    $("layer-stations").checked = state.layers.stations;
    $("layer-masks").checked = state.layers.masks;
    $("background-opacity").value = state.layers.backgroundOpacity;
    $("background-wash").value = state.layers.backgroundWash;
    $("background-opacity-output").textContent = `${Math.round(state.layers.backgroundOpacity * 100)}%`;
    $("background-wash-output").textContent = `${Math.round(state.layers.backgroundWash * 100)}%`;
    const routeLayers = $("route-layer-list");
    routeLayers.replaceChildren();
    for (const route of state.data?.routes || []) {
      const label = document.createElement("label");
      const name = document.createElement("span");
      name.className = "route-layer-name";
      const swatch = document.createElement("span");
      swatch.className = "color-swatch";
      swatch.style.backgroundColor = route.color || "#ef4444";
      const text = document.createElement("span");
      text.textContent = route.name || route.id;
      name.append(swatch, text);
      const toggle = document.createElement("input");
      toggle.type = "checkbox";
      toggle.checked = state.layers.routes[route.id] !== false;
      toggle.addEventListener("change", () => this.handlers.toggleRouteLayer(route.id, toggle.checked));
      label.append(name, toggle);
      routeLayers.append(label);
    }
  }

  renderRuntime() {
    const runtime = this.store.getState().runtime || {};
    const phase = runtime.phase || "waiting";
    const labels = {
      waiting: "等待 BlueMap",
      debounce: "等待防抖",
      scanning: "扫描中",
      rendering: "等待渲染",
      ready: "已更新",
      error: "运行异常",
    };
    const pill = $("runtime-pill");
    pill.dataset.phase = phase;
    pill.textContent = labels[phase] || phase;
    const details = $("runtime-details");
    details.replaceChildren();
    const rows = [
      ["状态", labels[phase] || phase],
      ["当前扫描", `${runtime.scannedChunks || 0} / 剩余 ${runtime.remainingChunks || 0}`],
      ["待处理区块", String(runtime.pendingChunks || 0)],
      ["缓存区块", String(runtime.cachedChunks || 0)],
      ["缓存铁轨", String(runtime.cachedRails || 0)],
      ["最近扫描", formatTime(runtime.lastScanCompletedAt)],
      ["最近渲染", formatTime(runtime.lastRenderCompletedAt)],
    ];
    for (const [term, value] of rows) {
      const dt = document.createElement("dt");
      dt.textContent = term;
      const dd = document.createElement("dd");
      dd.textContent = value;
      details.append(dt, dd);
    }
  }

  renderSelectionBar() {
    const state = this.store.getState();
    const count = state.selectedComponents.size;
    $("selection-count").textContent = String(count);
    $("selection-actions").hidden = !state.adminMode || count === 0;
  }

  setInspector(text) {
    $("object-inspector").textContent = text || "尚未选择对象。";
  }

  setRouteInspector(text) {
    $("route-inspector").textContent = text;
  }

  setStationInspector(text) {
    $("station-inspector").textContent = text;
  }

  setMapMessage(message) {
    $("map-message").textContent = message;
  }

  toast(message, kind = "success", duration = 3200) {
    const toast = document.createElement("div");
    toast.className = `toast ${kind}`;
    toast.textContent = message;
    $("toast-region").append(toast);
    window.setTimeout(() => toast.remove(), duration);
  }

  async requestToken(currentToken = "") {
    const dialog = $("login-dialog");
    $("login-token").value = currentToken;
    $("login-error").hidden = true;
    dialog.showModal();
    const result = await new Promise((resolve) => {
      dialog.addEventListener("close", () => resolve(dialog.returnValue), { once: true });
    });
    return result === "default" ? $("login-token").value : null;
  }

  showLoginError(message) {
    const error = $("login-error");
    error.textContent = message;
    error.hidden = false;
    if (!$("login-dialog").open) {
      $("login-dialog").showModal();
    }
  }

  confirm(title, message) {
    const dialog = $("confirm-dialog");
    $("confirm-title").textContent = title;
    $("confirm-message").textContent = message;
    dialog.showModal();
    return new Promise((resolve) => {
      dialog.addEventListener("close", () => resolve(dialog.returnValue === "confirm"), { once: true });
    });
  }

}

export function shortComponentId(componentId) {
  const text = String(componentId || "");
  const index = text.lastIndexOf(":");
  return index >= 0 ? text.slice(index + 1) : text;
}

function formatTime(value) {
  const timestamp = Number(value) || 0;
  if (timestamp <= 0) {
    return "尚无";
  }
  return new Date(timestamp).toLocaleString("zh-CN", { hour12: false });
}
