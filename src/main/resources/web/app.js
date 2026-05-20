const state = {
  data: null,
  viewBox: { x: -128, y: -128, w: 256, h: 256 },
  selectedComponents: new Set(),
  toolMode: "pan",
  dragging: null,
  draftStation: null,
  draftMask: null,
  panning: null,
  backgroundKey: "",
  adminMode: false,
  autoLoadRoute: localStorage.getItem("bluemaprailway-auto-load-route") !== "false",
  snapGrid: localStorage.getItem("bluemaprailway-snap-grid") !== "false",
};

const $ = (id) => document.getElementById(id);
const svg = $("map");
const layers = {
  background: $("background-layer"),
  lines: $("line-layer"),
  masks: $("mask-layer"),
  stations: $("station-layer"),
  selection: $("selection-layer"),
};

const modeButtons = {
  "route-box": $("route-box-mode"),
  station: $("station-mode"),
  mask: $("mask-mode"),
};

$("token").value = localStorage.getItem("bluemaprailway-token") || "";
$("bg-opacity").value = localStorage.getItem("bluemaprailway-bg-opacity") || "0.72";
$("bg-wash").value = localStorage.getItem("bluemaprailway-bg-wash") || "0";
$("auto-load-route").checked = state.autoLoadRoute;
$("snap-grid").checked = state.snapGrid;

$("reload").addEventListener("click", loadState);
$("fit").addEventListener("click", fitBounds);
$("rescan").addEventListener("click", rescan);
$("toggle-admin").addEventListener("click", toggleAdminMode);
$("save-route").addEventListener("click", saveRoute);
$("save-station").addEventListener("click", saveStation);
$("save-mask").addEventListener("click", saveMask);
$("clear-selection").addEventListener("click", () => {
  state.selectedComponents.clear();
  renderOverlays();
  setStatus("已清空当前线路选择");
});
$("route-box-mode").addEventListener("click", () => toggleToolMode("route-box"));
$("station-mode").addEventListener("click", () => toggleToolMode("station"));
$("mask-mode").addEventListener("click", () => toggleToolMode("mask"));

$("token").addEventListener("change", () => {
  localStorage.setItem("bluemaprailway-token", $("token").value);
});
$("bg-opacity").addEventListener("input", () => {
  localStorage.setItem("bluemaprailway-bg-opacity", $("bg-opacity").value);
  renderBackground();
});
$("bg-wash").addEventListener("input", () => {
  localStorage.setItem("bluemaprailway-bg-wash", $("bg-wash").value);
  renderBackground();
});
$("auto-load-route").addEventListener("change", () => {
  state.autoLoadRoute = $("auto-load-route").checked;
  localStorage.setItem("bluemaprailway-auto-load-route", String(state.autoLoadRoute));
});
$("snap-grid").addEventListener("change", () => {
  state.snapGrid = $("snap-grid").checked;
  localStorage.setItem("bluemaprailway-snap-grid", String(state.snapGrid));
  renderDraftLayer();
});

svg.addEventListener("wheel", (event) => {
  event.preventDefault();
  const point = svgPoint(event);
  const factor = event.deltaY < 0 ? 0.82 : 1.22;
  zoomAt(point.x, point.y, factor);
});

svg.addEventListener("mousedown", (event) => {
  const point = svgPoint(event);
  if (state.adminMode && state.toolMode !== "pan") {
    state.dragging = {
      mode: state.toolMode,
      start: point,
      current: point,
      appendSelection: event.shiftKey,
    };
    renderDraftLayer();
    return;
  }

  state.panning = { x: event.clientX, y: event.clientY, viewBox: { ...state.viewBox } };
});

svg.addEventListener("mousemove", (event) => {
  const point = svgPoint(event);
  updateCoords(point);

  if (state.dragging) {
    state.dragging.current = point;
    renderDraftLayer();
    return;
  }

  if (state.panning) {
    const dx = (event.clientX - state.panning.x) * state.viewBox.w / svg.clientWidth;
    const dy = (event.clientY - state.panning.y) * state.viewBox.h / svg.clientHeight;
    setViewBox({
      ...state.viewBox,
      x: state.panning.viewBox.x - dx,
      y: state.panning.viewBox.y - dy,
    });
  }
});

svg.addEventListener("mouseleave", () => {
  $("coords").textContent = "X -- Z --";
});

window.addEventListener("mouseup", () => {
  if (state.dragging) {
    applyDragAction(state.dragging);
    state.dragging = null;
    renderDraftLayer();
  }
  state.panning = null;
});

loadState();

async function loadState() {
  setStatus("加载中...");
  const response = await fetch(`/api/state?token=${encodeURIComponent(token())}`);
  const data = await response.json();
  if (!data.ok) {
    setStatus(data.error || "加载失败");
    return;
  }

  state.data = data;
  if (state.adminMode && data.masks.length === 0) {
    state.adminMode = false;
  }
  applyModeState();
  render();
  fitBounds();
  const baseStatus = `线路 ${data.routes.length} / component ${data.components.length} / 站点 ${data.stations.length}`;
  setStatus(state.adminMode ? `${baseStatus} / 裁切 ${data.masks.length}` : baseStatus);
}

function render() {
  renderBackground();
  renderOverlays();
  renderLists();
}

function applyModeState() {
  document.body.classList.toggle("admin-mode", state.adminMode);
  $("toggle-admin").textContent = state.adminMode ? "退出管理" : "进入管理";
  $("mode-badge").textContent = state.adminMode ? "管理模式" : "浏览模式";
  if (!state.adminMode) {
    setToolMode("pan");
    state.selectedComponents.clear();
    state.draftStation = null;
    state.draftMask = null;
  }
}

async function toggleAdminMode() {
  if (state.adminMode) {
    state.adminMode = false;
    applyModeState();
    render();
    setStatus("已切换到浏览模式");
    return;
  }

  setStatus("验证管理密钥...");
  const response = await fetch(`/api/auth-check?token=${encodeURIComponent(token())}`);
  const data = await response.json();
  if (!data.ok || !data.admin) {
    state.adminMode = false;
    applyModeState();
    setStatus("密钥无效，仍处于浏览模式");
    return;
  }

  state.adminMode = true;
  applyModeState();
  await loadState();
  setStatus("已进入管理模式");
}

function renderOverlays() {
  layers.lines.replaceChildren();
  layers.masks.replaceChildren();
  layers.stations.replaceChildren();
  renderLines();
  renderMasks();
  renderStations();
  renderDraftLayer();
  renderSelectedComponents();
}

function renderBackground() {
  if (!state.data?.background) {
    return;
  }

  const background = state.data.background;
  const key = `${background.imageUrl}|${background.centerX}|${background.centerZ}|${background.pixelsPerBlock}|${token()}`;
  const existingImage = layers.background.querySelector("image");
  const existingWash = layers.background.querySelector("rect");
  if (state.backgroundKey === key && existingImage && existingWash) {
    existingImage.setAttribute("opacity", $("bg-opacity").value);
    existingWash.setAttribute("opacity", $("bg-wash").value);
    return;
  }

  const image = document.createElementNS("http://www.w3.org/2000/svg", "image");
  const wash = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  const probe = new Image();
  probe.onload = () => {
    const width = probe.naturalWidth / background.pixelsPerBlock;
    const height = probe.naturalHeight / background.pixelsPerBlock;
    const x = background.centerX - width / 2;
    const y = background.centerZ - height / 2;
    image.setAttribute("href", `${background.imageUrl}?token=${encodeURIComponent(token())}`);
    image.setAttribute("x", x);
    image.setAttribute("y", y);
    image.setAttribute("width", width);
    image.setAttribute("height", height);
    image.setAttribute("opacity", $("bg-opacity").value);
    wash.setAttribute("x", x);
    wash.setAttribute("y", y);
    wash.setAttribute("width", width);
    wash.setAttribute("height", height);
    wash.setAttribute("fill", "#ffffff");
    wash.setAttribute("opacity", $("bg-wash").value);
    layers.background.replaceChildren(image, wash);
    state.backgroundKey = key;
  };
  probe.src = `${background.imageUrl}?token=${encodeURIComponent(token())}`;
}

function renderLines() {
  for (const line of state.data.lines) {
    const polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
    polyline.setAttribute("class", "rail-line");
    polyline.setAttribute("points", line.points.map((point) => `${point[0]},${point[2]}`).join(" "));
    polyline.setAttribute("stroke", line.color || "#6b7280");
    polyline.dataset.componentId = line.componentId;
    if (state.selectedComponents.has(line.componentId)) {
      polyline.classList.add("selected");
    }
    polyline.addEventListener("click", (event) => {
      event.stopPropagation();
      selectComponent(line.componentId);
    });
    layers.lines.appendChild(polyline);
  }
}

function renderMasks() {
  for (const mask of state.data.masks) {
    const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    rect.setAttribute("class", "mask-box");
    if (!mask.enabled) {
      rect.classList.add("disabled");
    }
    rect.setAttribute("x", mask.minX);
    rect.setAttribute("y", mask.minZ);
    rect.setAttribute("width", mask.maxX - mask.minX + 1);
    rect.setAttribute("height", mask.maxZ - mask.minZ + 1);
    rect.addEventListener("click", (event) => {
      event.stopPropagation();
      inspect(`裁切规则 ${mask.id}\n名称: ${mask.name}\n世界: ${mask.world}\n范围: ${mask.minX},${mask.minY},${mask.minZ} -> ${mask.maxX},${mask.maxY},${mask.maxZ}`);
      if (state.adminMode) {
        fillMaskForm(mask);
      }
    });
    layers.masks.appendChild(rect);
  }
}

function renderStations() {
  for (const station of state.data.stations) {
    const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    rect.setAttribute("class", "station-box");
    rect.setAttribute("x", station.minX);
    rect.setAttribute("y", station.minZ);
    rect.setAttribute("width", station.maxX - station.minX + 1);
    rect.setAttribute("height", station.maxZ - station.minZ + 1);
    rect.addEventListener("click", (event) => {
      event.stopPropagation();
      inspect(`站点 ${station.id}\n名称: ${station.name}\n世界: ${station.world}\n范围: ${station.minX},${station.minY},${station.minZ} -> ${station.maxX},${station.maxY},${station.maxZ}`);
      if (state.adminMode) {
        fillStationForm(station);
      }
    });
    layers.stations.appendChild(rect);
  }
}

function renderDraftLayer() {
  layers.selection.replaceChildren();

  if (state.draftMask) {
    appendDraftRect(state.draftMask, "draft-box mask persisted", true);
  }
  if (state.draftStation) {
    appendDraftRect(state.draftStation, "draft-box persisted", true);
  }
  if (!state.dragging) {
    return;
  }

  const preview = previewBoxFromDrag(state.dragging);
  let className = "draft-box";
  if (state.dragging.mode === "route-box") {
    className += " route-select";
  } else if (state.dragging.mode === "mask") {
    className += " mask";
  }
  appendDraftRect(preview, className, state.snapGrid && state.dragging.mode !== "route-box");
}

function appendDraftRect(box, className, inclusive) {
  const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  rect.setAttribute("class", className);
  rect.setAttribute("x", box.minX);
  rect.setAttribute("y", box.minZ);
  rect.setAttribute("width", inclusive ? box.maxX - box.minX + 1 : box.maxX - box.minX);
  rect.setAttribute("height", inclusive ? box.maxZ - box.minZ + 1 : box.maxZ - box.minZ);
  layers.selection.appendChild(rect);
}

function applyDragAction(dragging) {
  if (!state.adminMode) {
    return;
  }
  if (dragging.mode === "station") {
    applyDraftStation(dragging);
    return;
  }
  if (dragging.mode === "mask") {
    applyDraftMask(dragging);
    return;
  }
  if (dragging.mode === "route-box") {
    applyRouteBoxSelection(dragging);
  }
}

function applyDraftStation(dragging) {
  const box = finalIntegerBox(dragging);
  state.draftStation = box;
  $("station-min-x").value = box.minX;
  $("station-min-z").value = box.minZ;
  $("station-max-x").value = box.maxX;
  $("station-max-z").value = box.maxZ;
  $("station-world").value = currentWorld();
  inspect(`已框选站点范围\n${box.minX},${box.minZ} -> ${box.maxX},${box.maxZ}`);
}

function applyDraftMask(dragging) {
  const box = finalIntegerBox(dragging);
  state.draftMask = box;
  $("mask-min-x").value = box.minX;
  $("mask-min-z").value = box.minZ;
  $("mask-max-x").value = box.maxX;
  $("mask-max-z").value = box.maxZ;
  $("mask-world").value = currentWorld();
  inspect(`已框选线路裁切范围\n${box.minX},${box.minZ} -> ${box.maxX},${box.maxZ}`);
}

function applyRouteBoxSelection(dragging) {
  const box = selectionBoxFromDrag(dragging);
  const matching = matchingComponents(box);
  if (!dragging.appendSelection) {
    state.selectedComponents.clear();
  }
  for (const componentId of matching) {
    state.selectedComponents.add(componentId);
  }

  renderOverlays();
  renderLists();
  if (matching.size === 0) {
    setStatus("框选范围内没有命中可见线路");
    return;
  }
  inspect(`已框选 ${matching.size} 个 component\n按住 Shift 再框选可追加到当前选择`);
  setStatus(`已框选 ${matching.size} 个 component`);
}

function matchingComponents(box) {
  const componentIds = new Set();
  for (const line of state.data.lines) {
    if (line.world !== currentWorld()) {
      continue;
    }
    if (lineTouchesBox(line, box)) {
      componentIds.add(line.componentId);
    }
  }
  return componentIds;
}

function lineTouchesBox(line, box) {
  const points = line.points || [];
  for (const point of points) {
    if (pointInBox2D(point[0], point[2], box)) {
      return true;
    }
  }

  for (let i = 1; i < points.length; i++) {
    if (segmentIntersectsBox2D(points[i - 1][0], points[i - 1][2], points[i][0], points[i][2], box)) {
      return true;
    }
  }

  return false;
}

function pointInBox2D(x, z, box) {
  return x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ;
}

function segmentIntersectsBox2D(x1, z1, x2, z2, box) {
  let t0 = 0;
  let t1 = 1;
  const dx = x2 - x1;
  const dz = z2 - z1;
  const edges = [
    [-dx, x1 - box.minX],
    [dx, box.maxX - x1],
    [-dz, z1 - box.minZ],
    [dz, box.maxZ - z1],
  ];

  for (const [p, q] of edges) {
    if (Math.abs(p) < 1.0E-6) {
      if (q < 0) {
        return false;
      }
      continue;
    }

    const ratio = q / p;
    if (p < 0) {
      t0 = Math.max(t0, ratio);
    } else {
      t1 = Math.min(t1, ratio);
    }
    if (t0 > t1) {
      return false;
    }
  }

  return true;
}

function toggleToolMode(mode) {
  setToolMode(state.toolMode === mode ? "pan" : mode);
}

function setToolMode(mode) {
  state.toolMode = mode;
  for (const [key, button] of Object.entries(modeButtons)) {
    button.classList.toggle("active", key === mode);
  }
  svg.classList.toggle("selecting", mode !== "pan");
}

function selectComponent(componentId) {
  if (!state.adminMode) {
    const component = state.data.components.find((item) => item.id === componentId);
    if (component) {
      inspectComponent(component);
    }
    return;
  }

  const component = state.data.components.find((item) => item.id === componentId);
  const activeRouteId = $("route-id").value.trim();
  if (
    state.autoLoadRoute &&
    component?.routeId &&
    activeRouteId !== component.routeId &&
    state.selectedComponents.size === 0
  ) {
    const route = state.data.routes.find((item) => item.id === component.routeId);
    if (route) {
      fillRouteForm(route, true);
      inspectComponent(component);
      renderOverlays();
      renderLists();
      return;
    }
  }

  if (state.selectedComponents.has(componentId)) {
    state.selectedComponents.delete(componentId);
  } else {
    state.selectedComponents.add(componentId);
  }

  if (component) {
    if (component.routeId) {
      const route = state.data.routes.find((item) => item.id === component.routeId);
      if (route) {
        fillRouteFields(route);
      }
    }
    inspectComponent(component);
  }

  renderOverlays();
  renderLists();
}

function inspectComponent(component) {
  inspect(`Component\n${component.id}\n世界: ${component.world}\n点数: ${component.pointCount}\n长度: ${component.length}\n线路: ${component.routeName || "未分类"}`);
}

function fillRouteFields(route) {
  $("route-id").value = route.id;
  $("route-name").value = route.name;
  $("route-color").value = route.color || "#22c55e";
  $("route-width").value = route.lineWidth > 0 ? route.lineWidth : 1;
  $("route-auto").checked = route.autoMatch;
}

function fillRouteForm(route, replaceSelection = true) {
  fillRouteFields(route);
  if (replaceSelection) {
    state.selectedComponents = new Set(route.componentIds || []);
  }
}

function fillStationForm(station) {
  $("station-id").value = station.id;
  $("station-name").value = station.name;
  $("station-world").value = station.world;
  $("station-min-x").value = station.minX;
  $("station-min-y").value = station.minY;
  $("station-min-z").value = station.minZ;
  $("station-max-x").value = station.maxX;
  $("station-max-y").value = station.maxY;
  $("station-max-z").value = station.maxZ;
  state.draftStation = {
    minX: station.minX,
    minY: station.minY,
    minZ: station.minZ,
    maxX: station.maxX,
    maxY: station.maxY,
    maxZ: station.maxZ,
  };
  renderDraftLayer();
}

function fillMaskForm(mask) {
  $("mask-id").value = mask.id;
  $("mask-name").value = mask.name;
  $("mask-world").value = mask.world;
  $("mask-enabled").checked = mask.enabled;
  $("mask-min-x").value = mask.minX;
  $("mask-min-y").value = mask.minY;
  $("mask-min-z").value = mask.minZ;
  $("mask-max-x").value = mask.maxX;
  $("mask-max-y").value = mask.maxY;
  $("mask-max-z").value = mask.maxZ;
  state.draftMask = {
    minX: mask.minX,
    minY: mask.minY,
    minZ: mask.minZ,
    maxX: mask.maxX,
    maxY: mask.maxY,
    maxZ: mask.maxZ,
  };
  renderDraftLayer();
}

function renderSelectedComponents() {
  const list = $("selected-components");
  list.replaceChildren();
  for (const componentId of state.selectedComponents) {
    const item = document.createElement("li");
    item.textContent = componentId;
    list.appendChild(item);
  }
}

function renderLists() {
  renderMaskList();
  renderRouteList();
  renderStationList();
}

function renderMaskList() {
  const list = $("mask-list");
  const masks = state.data?.masks || [];
  $("mask-count").textContent = masks.length;
  list.replaceChildren();
  if (masks.length === 0) {
    list.appendChild(emptyListItem("还没有裁切规则"));
    return;
  }

  for (const mask of masks) {
    const item = document.createElement("div");
    item.className = "list-item";

    const main = document.createElement("div");
    main.className = "list-main";
    const title = document.createElement("div");
    title.className = "list-title";
    title.textContent = mask.name || mask.id;
    const meta = document.createElement("div");
    meta.className = "list-meta";
    meta.textContent = `${mask.id} / ${mask.world} / ${mask.enabled ? "启用" : "停用"} / ${mask.minX},${mask.minZ} -> ${mask.maxX},${mask.maxZ}`;
    main.append(title, meta);

    const actions = document.createElement("div");
    actions.className = "list-actions";
    const edit = document.createElement("button");
    edit.type = "button";
    edit.textContent = "编辑";
    edit.addEventListener("click", () => {
      fillMaskForm(mask);
      focusMask(mask);
      inspect(`裁切规则 ${mask.id}\n名称: ${mask.name}\n世界: ${mask.world}\n范围: ${mask.minX},${mask.minY},${mask.minZ} -> ${mask.maxX},${mask.maxY},${mask.maxZ}`);
    });
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "danger";
    remove.textContent = "删除";
    remove.addEventListener("click", () => deleteMask(mask.id));
    actions.append(edit, remove);

    item.append(main, actions);
    list.appendChild(item);
  }
}

function renderRouteList() {
  const list = $("route-list");
  const routes = state.data?.routes || [];
  $("route-count").textContent = routes.length;
  list.replaceChildren();
  if (routes.length === 0) {
    list.appendChild(emptyListItem("还没有线路"));
    return;
  }

  for (const route of routes) {
    const item = document.createElement("div");
    item.className = "list-item";

    const main = document.createElement("div");
    main.className = "list-main";
    const title = document.createElement("div");
    title.className = "list-title";
    const chip = document.createElement("span");
    chip.className = "color-chip";
    chip.style.background = route.color || "#6b7280";
    const name = document.createElement("span");
    name.textContent = route.name || route.id;
    title.append(chip, name);
    const meta = document.createElement("div");
    meta.className = "list-meta";
    meta.textContent = `${route.id} / ${route.componentIds?.length || 0} component`;
    main.append(title, meta);

    const actions = document.createElement("div");
    actions.className = "list-actions";
    if (state.adminMode) {
      const edit = document.createElement("button");
      edit.type = "button";
      edit.textContent = "编辑";
      edit.addEventListener("click", () => {
        fillRouteForm(route, true);
        inspect(`线路 ${route.id}\n名称: ${route.name}\ncomponent: ${(route.componentIds || []).length}`);
        focusRoute(route);
        renderOverlays();
        renderLists();
      });
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "danger";
      remove.textContent = "删除";
      remove.addEventListener("click", () => deleteRoute(route.id));
      actions.append(edit, remove);
    } else {
      const view = document.createElement("button");
      view.type = "button";
      view.textContent = "查看";
      view.addEventListener("click", () => {
        inspect(`线路 ${route.id}\n名称: ${route.name}\ncomponent: ${(route.componentIds || []).length}`);
        focusRoute(route);
      });
      actions.append(view);
    }

    item.append(main, actions);
    list.appendChild(item);
  }
}

function renderStationList() {
  const list = $("station-list");
  const stations = state.data?.stations || [];
  $("station-count").textContent = stations.length;
  list.replaceChildren();
  if (stations.length === 0) {
    list.appendChild(emptyListItem("还没有站点"));
    return;
  }

  for (const station of stations) {
    const item = document.createElement("div");
    item.className = "list-item";

    const main = document.createElement("div");
    main.className = "list-main";
    const title = document.createElement("div");
    title.className = "list-title";
    title.textContent = station.name || station.id;
    const meta = document.createElement("div");
    meta.className = "list-meta";
    meta.textContent = `${station.id} / ${station.world} / ${station.minX},${station.minZ} -> ${station.maxX},${station.maxZ}`;
    main.append(title, meta);

    const actions = document.createElement("div");
    actions.className = "list-actions";
    if (state.adminMode) {
      const edit = document.createElement("button");
      edit.type = "button";
      edit.textContent = "编辑";
      edit.addEventListener("click", () => {
        fillStationForm(station);
        focusStation(station);
        inspect(`站点 ${station.id}\n名称: ${station.name}\n世界: ${station.world}\n范围: ${station.minX},${station.minY},${station.minZ} -> ${station.maxX},${station.maxY},${station.maxZ}`);
      });
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "danger";
      remove.textContent = "删除";
      remove.addEventListener("click", () => deleteStation(station.id));
      actions.append(edit, remove);
    } else {
      const view = document.createElement("button");
      view.type = "button";
      view.textContent = "查看";
      view.addEventListener("click", () => {
        focusStation(station);
        inspect(`站点 ${station.id}\n名称: ${station.name}\n世界: ${station.world}\n范围: ${station.minX},${station.minY},${station.minZ} -> ${station.maxX},${station.maxY},${station.maxZ}`);
      });
      actions.append(view);
    }

    item.append(main, actions);
    list.appendChild(item);
  }
}

function emptyListItem(text) {
  const item = document.createElement("div");
  item.className = "list-item";
  const main = document.createElement("div");
  main.className = "list-meta";
  main.textContent = text;
  item.appendChild(main);
  return item;
}

async function saveRoute() {
  const routeId = $("route-id").value.trim();
  const existingRoute = state.data.routes.some((route) => route.id === routeId);
  if (state.selectedComponents.size === 0 && !existingRoute) {
    setStatus("请先点击地图上的铁路段，至少选择一个 component。");
    return;
  }

  const body = {
    id: routeId,
    name: $("route-name").value.trim(),
    color: $("route-color").value,
    lineWidth: Number($("route-width").value),
    autoMatch: $("route-auto").checked,
    componentIds: Array.from(state.selectedComponents),
  };
  const result = await postJson("/api/route", body);
  setStatus(result.ok ? (state.selectedComponents.size === 0 ? "线路绑定已清空，已排队重扫" : "线路已保存，已排队重扫") : result.error);
  if (result.ok) {
    await loadState();
  }
}

async function saveStation() {
  const body = {
    id: $("station-id").value.trim(),
    name: $("station-name").value.trim(),
    world: $("station-world").value.trim() || currentWorld(),
    minX: Number($("station-min-x").value),
    minY: Number($("station-min-y").value),
    minZ: Number($("station-min-z").value),
    maxX: Number($("station-max-x").value),
    maxY: Number($("station-max-y").value),
    maxZ: Number($("station-max-z").value),
  };
  const result = await postJson("/api/station", body);
  setStatus(result.ok ? "站点已保存，已排队重扫" : result.error);
  if (result.ok) {
    state.draftStation = null;
    renderDraftLayer();
    await loadState();
  }
}

async function saveMask() {
  const body = {
    id: $("mask-id").value.trim(),
    name: $("mask-name").value.trim(),
    world: $("mask-world").value.trim() || currentWorld(),
    enabled: $("mask-enabled").checked,
    minX: Number($("mask-min-x").value),
    minY: Number($("mask-min-y").value),
    minZ: Number($("mask-min-z").value),
    maxX: Number($("mask-max-x").value),
    maxY: Number($("mask-max-y").value),
    maxZ: Number($("mask-max-z").value),
  };
  const result = await postJson("/api/mask", body);
  setStatus(result.ok ? "裁切规则已保存，已排队重扫" : result.error);
  if (result.ok) {
    state.draftMask = null;
    renderDraftLayer();
    await loadState();
  }
}

async function deleteRoute(routeId) {
  if (!confirm(`删除线路 ${routeId}？`)) {
    return;
  }

  const result = await postJson("/api/route/delete", { id: routeId });
  setStatus(result.ok ? "线路已删除，已排队重扫" : result.error);
  if (result.ok) {
    if ($("route-id").value.trim() === routeId) {
      clearRouteForm();
    }
    await loadState();
  }
}

async function deleteStation(stationId) {
  if (!confirm(`删除站点 ${stationId}？`)) {
    return;
  }

  const result = await postJson("/api/station/delete", { id: stationId });
  setStatus(result.ok ? "站点已删除，已排队重扫" : result.error);
  if (result.ok) {
    if ($("station-id").value.trim() === stationId) {
      clearStationForm();
    }
    await loadState();
  }
}

async function deleteMask(maskId) {
  if (!confirm(`删除裁切规则 ${maskId}？`)) {
    return;
  }

  const result = await postJson("/api/mask/delete", { id: maskId });
  setStatus(result.ok ? "裁切规则已删除，已排队重扫" : result.error);
  if (result.ok) {
    if ($("mask-id").value.trim() === maskId) {
      clearMaskForm();
    }
    await loadState();
  }
}

async function rescan() {
  const result = await postJson("/api/rescan", {});
  setStatus(result.ok ? "已排队重扫" : result.error);
}

async function postJson(path, body) {
  const response = await fetch(`${path}?token=${encodeURIComponent(token())}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-BlueMapRailway-Token": token() },
    body: JSON.stringify(body),
  });
  return response.json();
}

function clearRouteForm() {
  $("route-id").value = "";
  $("route-name").value = "";
  $("route-color").value = "#22c55e";
  $("route-width").value = 1;
  $("route-auto").checked = true;
  state.selectedComponents.clear();
  renderOverlays();
}

function clearStationForm() {
  $("station-id").value = "";
  $("station-name").value = "";
  $("station-world").value = currentWorld();
  $("station-min-x").value = "";
  $("station-min-y").value = 0;
  $("station-min-z").value = "";
  $("station-max-x").value = "";
  $("station-max-y").value = 80;
  $("station-max-z").value = "";
  state.draftStation = null;
  renderDraftLayer();
}

function clearMaskForm() {
  $("mask-id").value = "";
  $("mask-name").value = "";
  $("mask-world").value = currentWorld();
  $("mask-enabled").checked = true;
  $("mask-min-x").value = "";
  $("mask-min-y").value = 0;
  $("mask-min-z").value = "";
  $("mask-max-x").value = "";
  $("mask-max-y").value = 320;
  $("mask-max-z").value = "";
  state.draftMask = null;
  renderDraftLayer();
}

function previewBoxFromDrag(dragging) {
  const raw = rawBox(dragging.start, dragging.current);
  if (!state.snapGrid) {
    return raw;
  }
  return {
    minX: Math.floor(raw.minX),
    minZ: Math.floor(raw.minZ),
    maxX: Math.ceil(raw.maxX),
    maxZ: Math.ceil(raw.maxZ),
  };
}

function finalIntegerBox(dragging) {
  const raw = rawBox(dragging.start, dragging.current);
  return {
    minX: Math.floor(raw.minX),
    minY: 0,
    minZ: Math.floor(raw.minZ),
    maxX: Math.ceil(raw.maxX),
    maxY: 320,
    maxZ: Math.ceil(raw.maxZ),
  };
}

function selectionBoxFromDrag(dragging) {
  const raw = rawBox(dragging.start, dragging.current);
  if (!state.snapGrid) {
    return raw;
  }
  return {
    minX: Math.floor(raw.minX),
    minZ: Math.floor(raw.minZ),
    maxX: Math.ceil(raw.maxX),
    maxZ: Math.ceil(raw.maxZ),
  };
}

function rawBox(a, b) {
  return {
    minX: Math.min(a.x, b.x),
    minZ: Math.min(a.y, b.y),
    maxX: Math.max(a.x, b.x),
    maxZ: Math.max(a.y, b.y),
  };
}

function focusRoute(route) {
  const componentIds = new Set(route.componentIds || []);
  const points = state.data.lines
    .filter((line) => componentIds.has(line.componentId))
    .flatMap((line) => line.points.map((point) => ({ x: point[0], z: point[2] })));
  focusPoints(points);
}

function focusStation(station) {
  focusPoints([
    { x: station.minX, z: station.minZ },
    { x: station.maxX, z: station.maxZ },
  ]);
}

function focusMask(mask) {
  focusPoints([
    { x: mask.minX, z: mask.minZ },
    { x: mask.maxX, z: mask.maxZ },
  ]);
}

function focusPoints(points) {
  if (!points.length) {
    return;
  }

  const minX = Math.min(...points.map((point) => point.x));
  const maxX = Math.max(...points.map((point) => point.x));
  const minZ = Math.min(...points.map((point) => point.z));
  const maxZ = Math.max(...points.map((point) => point.z));
  const padding = 32;
  setViewBox({
    x: minX - padding,
    y: minZ - padding,
    w: Math.max(64, maxX - minX + padding * 2),
    h: Math.max(64, maxZ - minZ + padding * 2),
  });
}

function fitBounds() {
  const bounds = state.data?.bounds || { minX: -128, minZ: -128, maxX: 128, maxZ: 128 };
  const padding = 64;
  setViewBox({
    x: bounds.minX - padding,
    y: bounds.minZ - padding,
    w: Math.max(64, bounds.maxX - bounds.minX + padding * 2),
    h: Math.max(64, bounds.maxZ - bounds.minZ + padding * 2),
  });
}

function zoomAt(x, y, factor) {
  const nextW = state.viewBox.w * factor;
  const nextH = state.viewBox.h * factor;
  const rx = (x - state.viewBox.x) / state.viewBox.w;
  const ry = (y - state.viewBox.y) / state.viewBox.h;
  setViewBox({ x: x - nextW * rx, y: y - nextH * ry, w: nextW, h: nextH });
}

function setViewBox(viewBox) {
  state.viewBox = viewBox;
  svg.setAttribute("viewBox", `${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`);
  $("grid").setAttribute("x", viewBox.x - 2048);
  $("grid").setAttribute("y", viewBox.y - 2048);
  $("grid").setAttribute("width", viewBox.w + 4096);
  $("grid").setAttribute("height", viewBox.h + 4096);
}

function updateCoords(point) {
  $("coords").textContent = `X ${Math.floor(point.x)} Z ${Math.floor(point.y)}`;
}

function svgPoint(event) {
  const point = svg.createSVGPoint();
  point.x = event.clientX;
  point.y = event.clientY;
  const transformed = point.matrixTransform(svg.getScreenCTM().inverse());
  return { x: transformed.x, y: transformed.y };
}

function inspect(text) {
  $("inspector").textContent = text;
}

function setStatus(text) {
  $("status").textContent = text || "";
}

function token() {
  return $("token").value;
}

function currentWorld() {
  return state.data?.background?.world || "";
}
