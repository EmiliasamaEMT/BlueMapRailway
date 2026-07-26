import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { adminFixture, fixtureState } from "../fixtures/state.mjs";

const root = path.resolve("../core/src/main/resources/web");
const paperBackground = path.resolve("../paper/src/main/resources/web/default-background.png");
const mutations = [];

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, "http://127.0.0.1");
  const token = url.searchParams.get("token") || request.headers["x-bluemaprailway-token"] || "";
  if (url.pathname === "/api/auth-check") {
    return json(response, { ok: true, admin: token === "test-token" });
  }
  if (url.pathname === "/api/runtime") {
    return json(response, { ok: true, runtime: fixtureState.runtime });
  }
  if (url.pathname === "/api/state") {
    return json(response, token === "test-token" ? adminFixture() : fixtureState);
  }
  if (url.pathname.startsWith("/api/") && request.method === "POST") {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    mutations.push({ path: url.pathname, body: JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}") });
    return json(response, { ok: true });
  }
  if (url.pathname === "/__mutations") {
    return json(response, { ok: true, mutations });
  }
  if (url.pathname === "/background.png") {
    return file(response, paperBackground, "image/png");
  }
  const relative = url.pathname === "/" ? "index.html" : url.pathname.slice(1);
  if (relative.includes("..")) {
    response.writeHead(400).end();
    return;
  }
  const contentType = relative.endsWith(".html")
    ? "text/html; charset=utf-8"
    : relative.endsWith(".css")
      ? "text/css; charset=utf-8"
      : "text/javascript; charset=utf-8";
  return file(response, path.join(root, relative), contentType);
});

server.listen(18765, "127.0.0.1", () => console.log("Admin web mock server: http://127.0.0.1:18765"));

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => server.close(() => process.exit(0)));
}

function json(response, body) {
  response.writeHead(200, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}

async function file(response, filename, contentType) {
  try {
    response.writeHead(200, { "content-type": contentType });
    response.end(await fs.readFile(filename));
  } catch {
    response.writeHead(404).end("Not found");
  }
}
