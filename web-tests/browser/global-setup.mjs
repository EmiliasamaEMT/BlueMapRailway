import { closeMockServer, serverReady } from "./mock-server.mjs";

export default async function globalSetup() {
  await serverReady;
  return closeMockServer;
}
