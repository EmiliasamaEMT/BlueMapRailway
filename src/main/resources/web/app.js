const state = {
  data: null,
  viewBox: { x: -128, y: -128, w: 256, h: 256 },
  selectedComponents: new Set(),
  stationMode: false,
  dragging: null,
  draftStation: null,
  panning: null,
  backgroundKey: "",
};

const $ = (id) => document.getElementById(id);
const svg = $("map");
const layers = {
  background: $("background-layer"),
  lines: $("line-layer"),
  stations: $("station-layer"),
  selection: $("selection-layer"),
};

$("token").value = localStorage.getItem("bluemaprailway-token") || "";
$("bg-opacity").value = localStorage.getItem("bluemaprailway-bg-opacity") || "0.72";
$("bg-wash").value = localStorage.getItem("bluemaprailway-bg-wash") || "0";
$("reload").addEventListener("click", loadState);
$("fit").addEventListener("click", fitBounds);
$("rescan").addEventListener("click", rescan);
$("save-route").addEventListener("click", saveRoute);
$("save-station").addEventListener("click", saveStation);
$("station-mode").addEventListener("click", () => {
  state.stationMode = !state.stationMode;
  $("station-mode").classList.toggle("active", state.stationMode);
  svg.classList.toggle("selecting", state.stationMode);
});
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

svg.addEventListener("wheel", (event) => {
  event.preventDefault();
  const point = svgPoint(event);
  const factor = event.deltaY < 0 ? 0.82 : 1.22;
  zoomAt(point.x, point.y, factor);
});

svg.addEventListener("mousedown", (event) => {
  const point = svgPoint(event);
  if (state.stationMode) {
    state.dragging = { start: point, current: point };
    drawDraftBox();
    return;
  }
  state.panning = { x: event.clientX, y: event.clientY, viewBox: { ...state.viewBox } };
});

svg.addEventListener("mousemove", (event) => {
  updateCoords(svgPoint(event));
  if (state.dragging) {
    state.dragging.current = svgPoint(event);
    drawDraftBox();
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
    applyDraftStation();
    state.dragging = null;
    renderDraftBox();
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
  render();
  fitBounds();
  setStatus(`线路 ${data.routes.length} / component ${data.components.length} / 站点 ${data.stations.length}`);
}

function render() {
  renderBackground();
  renderOverlays();
  renderLists();
}

function renderOverlays() {
  layers.lines.replaceChildren();
  layers.stations.replaceChildren();
  renderLines();
  renderStations();
  renderDraftBox();
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
      fillStationForm(station);
      inspect(`站点 ${station.id}\n名称: ${station.name}\n世界: ${station.world}\n范围: ${station.minX},${station.minY},${station.minZ} -> ${station.maxX},${station.maxY},${station.maxZ}`);
    });
    layers.stations.appendChild(rect);
  }
}

function selectComponent(componentId) {
  const component = state.data.components.find((item) => item.id === componentId);
  const activeRouteId = $("route-id").value.trim();
  if (component?.routeId && activeRouteId !== component.routeId && state.selectedComponents.size === 0) {
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
      if (route) fillRouteFields(route);
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
    minZ: station.minZ,
    maxX: station.maxX,
    maxZ: station.maxZ,
  };
  renderDraftBox();
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
  renderRouteList();
  renderStationList();
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
  if (result.ok) await loadState();
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

async function saveStation() {
  const body = {
    id: $("station-id").value.trim(),
    name: $("station-name").value.trim(),
    world: $("station-world").value.trim() || state.data.background.world,
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
    renderDraftBox();
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

function applyDraftStation() {
  const a = state.dragging.start;
  const b = state.dragging.current;
  const minX = Math.floor(Math.min(a.x, b.x));
  const maxX = Math.ceil(Math.max(a.x, b.x));
  const minZ = Math.floor(Math.min(a.y, b.y));
  const maxZ = Math.ceil(Math.max(a.y, b.y));
  state.draftStation = { minX, minZ, maxX, maxZ };
  $("station-min-x").value = minX;
  $("station-max-x").value = maxX;
  $("station-min-z").value = minZ;
  $("station-max-z").value = maxZ;
  $("station-world").value = state.data.background.world;
  inspect(`已框选站点范围\n${minX},${minZ} -> ${maxX},${maxZ}`);
}

function drawDraftBox() {
  renderDraftBox();
}

function renderDraftBox() {
  layers.selection.replaceChildren();
  if (!state.dragging && !state.draftStation) {
    return;
  }

  const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  if (state.dragging) {
    const a = state.dragging.start;
    const b = state.dragging.current;
    rect.setAttribute("class", "draft-box");
    rect.setAttribute("x", Math.min(a.x, b.x));
    rect.setAttribute("y", Math.min(a.y, b.y));
    rect.setAttribute("width", Math.abs(a.x - b.x));
    rect.setAttribute("height", Math.abs(a.y - b.y));
  } else {
    const box = state.draftStation;
    rect.setAttribute("class", "draft-box persisted");
    rect.setAttribute("x", box.minX);
    rect.setAttribute("y", box.minZ);
    rect.setAttribute("width", box.maxX - box.minX + 1);
    rect.setAttribute("height", box.maxZ - box.minZ + 1);
  }
  layers.selection.replaceChildren(rect);
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
  $("station-world").value = state.data?.background?.world || "";
  $("station-min-x").value = "";
  $("station-min-y").value = 0;
  $("station-min-z").value = "";
  $("station-max-x").value = "";
  $("station-max-y").value = 80;
  $("station-max-z").value = "";
  state.draftStation = null;
  renderDraftBox();
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
