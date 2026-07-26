import { lineIntersectsBox } from "./geometry.mjs";
import { RailSpatialIndex } from "./spatial-index.mjs";
import { screenToWorld, worldToScreen } from "./viewport.mjs";

const SVG_NS = "http://www.w3.org/2000/svg";

export function normalizeRailLineWidth(lineWidth, fallback = 3) {
  const value = Number(lineWidth);
  return Number.isFinite(value) && value > 0
    ? Math.min(64, Math.max(1, value))
    : fallback;
}

export class RailwayMapView {
  constructor(store, elements) {
    this.store = store;
    this.stage = elements.stage;
    this.backgroundCanvas = elements.backgroundCanvas;
    this.railCanvas = elements.railCanvas;
    this.overlay = elements.overlay;
    this.maskLayer = elements.maskLayer;
    this.stationLayer = elements.stationLayer;
    this.selectionLayer = elements.selectionLayer;
    this.draftLayer = elements.draftLayer;
    this.index = new RailSpatialIndex(128);
    this.backgroundImage = null;
    this.backgroundKey = "";
    this.backgroundLoading = false;
    this.width = 1;
    this.height = 1;
    this.pixelRatio = Math.max(1, window.devicePixelRatio || 1);
    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.stage);
    this.resize();
  }

  destroy() {
    this.resizeObserver.disconnect();
  }

  resize() {
    const rect = this.stage.getBoundingClientRect();
    const width = Math.max(1, Math.round(rect.width));
    const height = Math.max(1, Math.round(rect.height));
    if (width === this.width && height === this.height) {
      return;
    }
    this.width = width;
    this.height = height;
    this.pixelRatio = Math.max(1, window.devicePixelRatio || 1);
    for (const canvas of [this.backgroundCanvas, this.railCanvas]) {
      canvas.width = Math.round(width * this.pixelRatio);
      canvas.height = Math.round(height * this.pixelRatio);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
    }
    this.renderAll();
  }

  setData(data) {
    this.index.rebuild(data?.lines || []);
    this.ensureBackgroundImage();
    this.renderAll();
  }

  renderAll() {
    this.drawBackground();
    this.drawRails();
    this.drawOverlay();
  }

  renderView() {
    this.drawBackground();
    this.drawRails();
    this.drawOverlay();
  }

  renderLayers() {
    this.drawBackground();
    this.drawRails();
    this.drawOverlay();
  }

  renderSelection() {
    this.drawSelection();
    this.drawDraft();
  }

  worldPoint(event) {
    return screenToWorld(this.store.getState().view, this.stage.getBoundingClientRect(), event.clientX, event.clientY);
  }

  nearestLine(point, pixelTolerance = 8) {
    const state = this.store.getState();
    const tolerance = Math.max(state.view.w / this.width, state.view.h / this.height) * pixelTolerance;
    const line = this.index.nearest(point.x, point.z, tolerance);
    return line && this.lineVisible(line, state) ? line : null;
  }

  componentsInBox(box) {
    const state = this.store.getState();
    const componentIds = new Set();
    for (const line of this.index.queryBox(box)) {
      if (this.lineVisible(line, state) && lineIntersectsBox(line, box)) {
        componentIds.add(line.componentId);
      }
    }
    return componentIds;
  }

  ensureBackgroundImage() {
    const state = this.store.getState();
    const background = state.data?.background;
    if (!background) {
      this.backgroundImage = null;
      this.backgroundKey = "";
      return;
    }
    const token = state.token || "";
    const key = `${background.imageUrl}|${token}`;
    if (key === this.backgroundKey || this.backgroundLoading) {
      return;
    }
    this.backgroundKey = key;
    this.backgroundLoading = true;
    const image = new Image();
    image.decoding = "async";
    image.onload = () => {
      if (this.backgroundKey === key) {
        this.backgroundImage = image;
        this.backgroundLoading = false;
        this.drawBackground();
      }
    };
    image.onerror = () => {
      if (this.backgroundKey === key) {
        this.backgroundImage = null;
        this.backgroundLoading = false;
        this.drawBackground();
      }
    };
    const url = new URL(background.imageUrl, window.location.origin);
    if (token) {
      url.searchParams.set("token", token);
    }
    image.src = url.toString();
  }

  drawBackground() {
    const state = this.store.getState();
    const context = this.prepareContext(this.backgroundCanvas);
    context.fillStyle = "#e7ebed";
    context.fillRect(0, 0, this.width, this.height);

    if (state.layers.background && this.backgroundImage && state.data?.background?.world === state.world) {
      const background = state.data.background;
      const pixelsPerBlock = Math.max(0.0001, Number(background.pixelsPerBlock) || 1);
      const worldWidth = this.backgroundImage.naturalWidth / pixelsPerBlock;
      const worldHeight = this.backgroundImage.naturalHeight / pixelsPerBlock;
      const minX = Number(background.centerX) - worldWidth / 2;
      const minZ = Number(background.centerZ) - worldHeight / 2;
      const topLeft = worldToScreen(state.view, this.width, this.height, minX, minZ);
      const bottomRight = worldToScreen(state.view, this.width, this.height, minX + worldWidth, minZ + worldHeight);
      context.globalAlpha = Number(state.layers.backgroundOpacity);
      context.imageSmoothingEnabled = false;
      context.drawImage(
        this.backgroundImage,
        topLeft.x,
        topLeft.y,
        bottomRight.x - topLeft.x,
        bottomRight.y - topLeft.y,
      );
      context.globalAlpha = 1;
    }

    const wash = Number(state.layers.backgroundWash);
    if (wash > 0) {
      context.fillStyle = `rgba(255,255,255,${Math.min(1, wash)})`;
      context.fillRect(0, 0, this.width, this.height);
    }
    if (state.layers.grid) {
      this.drawGrid(context, state.view);
    }
  }

  drawGrid(context, view) {
    const pixelsPerBlock = this.width / view.w;
    const smallStep = pixelsPerBlock >= 0.5 ? 16 : 128;
    const largeStep = 128;
    const drawLines = (step, color, lineWidth) => {
      context.beginPath();
      const startX = Math.floor(view.x / step) * step;
      const endX = view.x + view.w;
      for (let x = startX; x <= endX; x += step) {
        const screen = worldToScreen(view, this.width, this.height, x, view.y).x;
        context.moveTo(Math.round(screen) + 0.5, 0);
        context.lineTo(Math.round(screen) + 0.5, this.height);
      }
      const startZ = Math.floor(view.y / step) * step;
      const endZ = view.y + view.h;
      for (let z = startZ; z <= endZ; z += step) {
        const screen = worldToScreen(view, this.width, this.height, view.x, z).y;
        context.moveTo(0, Math.round(screen) + 0.5);
        context.lineTo(this.width, Math.round(screen) + 0.5);
      }
      context.strokeStyle = color;
      context.lineWidth = lineWidth;
      context.stroke();
    };
    if (smallStep < largeStep) {
      drawLines(smallStep, "rgba(68,82,94,0.13)", 1);
    }
    drawLines(largeStep, "rgba(47,60,72,0.25)", 1);
  }

  drawRails() {
    const state = this.store.getState();
    const context = this.prepareContext(this.railCanvas);
    const groups = new Map();
    for (const line of state.data?.lines || []) {
      if (!this.lineVisible(line, state)) {
        continue;
      }
      const color = line.color || "#f59e0b";
      const lineWidth = normalizeRailLineWidth(line.lineWidth);
      const key = `${color}|${lineWidth}`;
      const group = groups.get(key) || { color, lineWidth, lines: [] };
      group.lines.push(line);
      groups.set(key, group);
    }

    context.lineCap = "round";
    context.lineJoin = "round";
    for (const { color, lineWidth, lines } of groups.values()) {
      context.beginPath();
      for (const line of lines) {
        let first = true;
        for (const point of line.points || []) {
          const screen = worldToScreen(state.view, this.width, this.height, Number(point[0]), Number(point[2]));
          if (first) {
            context.moveTo(screen.x, screen.y);
            first = false;
          } else {
            context.lineTo(screen.x, screen.y);
          }
        }
      }
      context.strokeStyle = color;
      context.lineWidth = lineWidth;
      context.stroke();
    }
  }

  lineVisible(line, state = this.store.getState()) {
    if (!line || line.world !== state.world) {
      return false;
    }
    const routeId = line.routeId || "";
    if (!routeId) {
      return state.layers.unclassified;
    }
    return state.layers.routes[routeId] !== false;
  }

  drawOverlay() {
    const state = this.store.getState();
    this.overlay.setAttribute("viewBox", `${state.view.x} ${state.view.y} ${state.view.w} ${state.view.h}`);
    this.drawMasks();
    this.drawStations();
    this.drawSelection();
    this.drawDraft();
  }

  drawMasks() {
    this.maskLayer.replaceChildren();
    const state = this.store.getState();
    if (!state.adminMode) {
      return;
    }
    for (const mask of state.data?.masks || []) {
      const focused = state.focused.type === "mask" && state.focused.id === mask.id;
      if (mask.world !== state.world || (!state.layers.masks && !focused)) {
        continue;
      }
      const rect = this.boxElement(mask, "mask-box");
      if (focused) {
        rect.classList.add("focused");
      }
      if (!mask.enabled) {
        rect.classList.add("disabled");
      }
      rect.dataset.objectType = "mask";
      rect.dataset.objectId = mask.id;
      this.maskLayer.append(rect);
    }
  }

  drawStations() {
    this.stationLayer.replaceChildren();
    const state = this.store.getState();
    if (!state.layers.stations) {
      return;
    }
    for (const station of state.data?.stations || []) {
      if (station.world !== state.world) {
        continue;
      }
      const rect = this.boxElement(station, "station-box");
      if (state.focused.type === "station" && state.focused.id === station.id) {
        rect.classList.add("focused");
      }
      rect.dataset.objectType = "station";
      rect.dataset.objectId = station.id;
      this.stationLayer.append(rect);
    }
  }

  drawSelection() {
    this.selectionLayer.replaceChildren();
    const state = this.store.getState();
    if (!state.adminMode || state.selectedComponents.size === 0) {
      return;
    }
    for (const line of state.data?.lines || []) {
      if (!state.selectedComponents.has(line.componentId) || !this.lineVisible(line, state)) {
        continue;
      }
      const polyline = document.createElementNS(SVG_NS, "polyline");
      polyline.setAttribute("class", "selection-line");
      polyline.setAttribute("points", (line.points || []).map((point) => `${point[0]},${point[2]}`).join(" "));
      this.selectionLayer.append(polyline);
    }
  }

  drawDraft() {
    this.draftLayer.replaceChildren();
    const draft = this.store.getState().draftBox;
    if (!draft?.box) {
      return;
    }
    const rect = this.boxElement(draft.box, `draft-box ${draft.mode || ""}`);
    this.draftLayer.append(rect);
  }

  boxElement(box, className) {
    const rect = document.createElementNS(SVG_NS, "rect");
    rect.setAttribute("class", className);
    rect.setAttribute("x", Number(box.minX));
    rect.setAttribute("y", Number(box.minZ));
    rect.setAttribute("width", Math.max(0.01, Number(box.maxX) - Number(box.minX) + 1));
    rect.setAttribute("height", Math.max(0.01, Number(box.maxZ) - Number(box.minZ) + 1));
    return rect;
  }

  prepareContext(canvas) {
    const context = canvas.getContext("2d");
    context.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
    context.clearRect(0, 0, this.width, this.height);
    return context;
  }
}
