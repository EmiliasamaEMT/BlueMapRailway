const state = {
  data: null,
  viewBox: { x: -128, y: -128, w: 256, h: 256 },
  selectedComponents: new Set(),
  stationMode: false,
  dragging: null,
  panning: null,
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

window.addEventListener("mouseup", () => {
  if (state.dragging) {
    applyDraftStation();
    state.dragging = null;
    layers.selection.replaceChildren();
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
  layers.background.replaceChildren();
  layers.lines.replaceChildren();
  layers.stations.replaceChildren();
  renderBackground();
  renderLines();
  renderStations();
  renderSelectedComponents();
}

function renderBackground() {
  const background = state.data.background;
  const image = document.createElementNS("http://www.w3.org/2000/svg", "image");
  const probe = new Image();
  probe.onload = () => {
    const width = probe.naturalWidth / background.pixelsPerBlock;
    const height = probe.naturalHeight / background.pixelsPerBlock;
    image.setAttribute("href", `${background.imageUrl}?token=${encodeURIComponent(token())}&t=${Date.now()}`);
    image.setAttribute("x", background.centerX - width / 2);
    image.setAttribute("y", background.centerZ - height / 2);
    image.setAttribute("width", width);
    image.setAttribute("height", height);
    image.setAttribute("opacity", "0.72");
    layers.background.replaceChildren(image);
  };
  probe.src = `${background.imageUrl}?token=${encodeURIComponent(token())}&t=${Date.now()}`;
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
  if (state.selectedComponents.has(componentId)) {
    state.selectedComponents.delete(componentId);
  } else {
    state.selectedComponents.add(componentId);
  }
  const component = state.data.components.find((item) => item.id === componentId);
  if (component) {
    if (component.routeId) {
      const route = state.data.routes.find((item) => item.id === component.routeId);
      if (route) fillRouteForm(route);
    }
    inspect(`Component\n${component.id}\n世界: ${component.world}\n点数: ${component.pointCount}\n长度: ${component.length}\n线路: ${component.routeName || "未分类"}`);
  }
  render();
}

function fillRouteForm(route) {
  $("route-id").value = route.id;
  $("route-name").value = route.name;
  $("route-color").value = route.color || "#22c55e";
  $("route-width").value = route.lineWidth > 0 ? route.lineWidth : 6;
  $("route-auto").checked = route.autoMatch;
  state.selectedComponents = new Set(route.componentIds || []);
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

async function saveRoute() {
  const body = {
    id: $("route-id").value.trim(),
    name: $("route-name").value.trim(),
    color: $("route-color").value,
    lineWidth: Number($("route-width").value),
    autoMatch: $("route-auto").checked,
    componentIds: Array.from(state.selectedComponents),
  };
  const result = await postJson("/api/route", body);
  setStatus(result.ok ? "线路已保存，已排队重扫" : result.error);
  if (result.ok) await loadState();
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
  if (result.ok) await loadState();
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
  $("station-min-x").value = minX;
  $("station-max-x").value = maxX;
  $("station-min-z").value = minZ;
  $("station-max-z").value = maxZ;
  $("station-world").value = state.data.background.world;
  inspect(`已框选站点范围\n${minX},${minZ} -> ${maxX},${maxZ}`);
}

function drawDraftBox() {
  const a = state.dragging.start;
  const b = state.dragging.current;
  const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  rect.setAttribute("class", "draft-box");
  rect.setAttribute("x", Math.min(a.x, b.x));
  rect.setAttribute("y", Math.min(a.y, b.y));
  rect.setAttribute("width", Math.abs(a.x - b.x));
  rect.setAttribute("height", Math.abs(a.y - b.y));
  layers.selection.replaceChildren(rect);
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
