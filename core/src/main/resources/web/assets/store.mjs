export function createInitialState(overrides = {}) {
  return {
    data: null,
    runtime: null,
    adminMode: false,
    token: "",
    world: "",
    activeTab: "routes",
    toolMode: "pan",
    view: { x: -128, y: -128, w: 256, h: 256 },
    selectedComponents: new Set(),
    focused: { type: "", id: "" },
    draftBox: null,
    dragging: null,
    panning: null,
    layers: {
      background: true,
      grid: true,
      unclassified: true,
      stations: true,
      masks: false,
      backgroundOpacity: 0.72,
      backgroundWash: 0,
      routes: {},
    },
    filters: {
      routeQuery: "",
      routeKind: "all",
      stationQuery: "",
      maskQuery: "",
    },
    preferences: {
      autoLoadRoute: true,
      snapGrid: true,
    },
    routeDraft: blankRouteDraft(),
    stationDraft: blankStationDraft(),
    maskDraft: blankMaskDraft(),
    dirty: { route: false, station: false, mask: false },
    loading: false,
    lastLoadedAt: 0,
    lastSeenRenderAt: 0,
    ...overrides,
  };
}

export function createStore(initialState = createInitialState()) {
  let state = initialState;
  const listeners = new Set();

  return {
    getState() {
      return state;
    },

    setState(patch, reason = "update") {
      const nextPatch = typeof patch === "function" ? patch(state) : patch;
      if (!nextPatch || typeof nextPatch !== "object") {
        return state;
      }
      state = { ...state, ...nextPatch };
      for (const listener of listeners) {
        listener(state, reason);
      }
      return state;
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

export function blankRouteDraft(world = "") {
  return {
    id: "",
    name: "",
    color: "#22c55e",
    lineWidth: 3,
    autoMatch: true,
    componentIds: [],
    world,
  };
}

export function blankStationDraft(world = "") {
  return {
    id: "",
    name: "",
    world,
    minX: "",
    minY: 0,
    minZ: "",
    maxX: "",
    maxY: 80,
    maxZ: "",
  };
}

export function blankMaskDraft(world = "") {
  return {
    id: "",
    name: "",
    world,
    enabled: true,
    minX: "",
    minY: 0,
    minZ: "",
    maxX: "",
    maxY: 320,
    maxZ: "",
  };
}

export function cloneSelection(selection) {
  return new Set(selection || []);
}

export function setDirty(state, editor, value = true) {
  return { dirty: { ...state.dirty, [editor]: value } };
}

export function selectionWithToggle(selection, componentId, append = true) {
  const next = new Set(append ? selection : []);
  if (next.has(componentId)) {
    next.delete(componentId);
  } else {
    next.add(componentId);
  }
  return next;
}
