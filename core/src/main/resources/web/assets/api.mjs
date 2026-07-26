export class ApiError extends Error {
  constructor(message, status = 0, payload = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.payload = payload;
  }
}

export class RailwayApi {
  constructor(tokenProvider = () => "") {
    this.tokenProvider = tokenProvider;
  }

  state() {
    return this.request("/api/state");
  }

  async runtime() {
    const payload = await this.request("/api/runtime");
    return payload.runtime || payload;
  }

  authCheck(token) {
    return this.request("/api/auth-check", { token });
  }

  saveRoute(route) {
    return this.request("/api/route", { method: "POST", body: route });
  }

  deleteRoute(id) {
    return this.request("/api/route/delete", { method: "POST", body: { id } });
  }

  saveStation(station) {
    return this.request("/api/station", { method: "POST", body: station });
  }

  deleteStation(id) {
    return this.request("/api/station/delete", { method: "POST", body: { id } });
  }

  saveMask(mask) {
    return this.request("/api/mask", { method: "POST", body: mask });
  }

  deleteMask(id) {
    return this.request("/api/mask/delete", { method: "POST", body: { id } });
  }

  saveHiddenLine(rule) {
    return this.request("/api/hide-line", { method: "POST", body: rule });
  }

  deleteHiddenLine(id) {
    return this.request("/api/hide-line/delete", { method: "POST", body: { id } });
  }

  rescan() {
    return this.request("/api/rescan", { method: "POST", body: {} });
  }

  async request(path, options = {}) {
    const token = options.token ?? this.tokenProvider() ?? "";
    const method = options.method || "GET";
    const url = new URL(path, window.location.origin);
    if (token) {
      url.searchParams.set("token", token);
    }

    const headers = { Accept: "application/json" };
    const init = { method, headers };
    if (token) {
      headers["X-BlueMapRailway-Token"] = token;
    }
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body);
    }

    let response;
    try {
      response = await fetch(url, init);
    } catch (error) {
      throw new ApiError(error?.message || "无法连接管理服务");
    }

    let payload;
    try {
      payload = await response.json();
    } catch {
      throw new ApiError(`服务器返回了无效响应 (${response.status})`, response.status);
    }

    if (!response.ok || payload?.ok === false) {
      throw new ApiError(payload?.error || `请求失败 (${response.status})`, response.status, payload);
    }
    return payload;
  }
}
