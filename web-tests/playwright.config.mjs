import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./browser",
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:18765",
    screenshot: "only-on-failure"
  },
  webServer: {
    command: "node browser/mock-server.mjs",
    port: 18765,
    reuseExistingServer: false
  },
  projects: [
    { name: "desktop", use: { viewport: { width: 1440, height: 900 } } },
    { name: "narrow", use: { viewport: { width: 768, height: 900 } } }
  ]
});
