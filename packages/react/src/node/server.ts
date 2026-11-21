import { spawn, type ChildProcess } from "node:child_process";
import path from "node:path";
import type { ServerConfig } from "./config.js";

async function answers(url: string): Promise<boolean> {
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(2000) });
    await r.body?.cancel();
    return true;
  } catch {
    return false;
  }
}

export interface RunningServer {
  stop(): Promise<void>;
}

/** Starts the host page server unless one already answers at [baseURL]; resolves once it answers. */
export async function startServer(server: ServerConfig | undefined, baseURL: string, dir: string, log: (s: string) => void): Promise<RunningServer> {
  const none = { stop: async () => {} };
  if (!server) return none;
  if ((server.reuseExisting ?? true) && (await answers(baseURL))) {
    log(`vcase: using the server already at ${baseURL}`);
    return none;
  }
  log(`vcase: starting \`${server.command}\``);
  const child: ChildProcess = spawn(server.command, {
    cwd: path.resolve(dir, server.cwd ?? "."),
    shell: true,
    detached: process.platform !== "win32",
    stdio: ["ignore", "inherit", "inherit"],
  });
  let exited: number | null = null;
  child.on("exit", (code) => (exited = code ?? -1));
  const stop = async () => {
    if (exited !== null || child.pid === undefined) return;
    try {
      if (process.platform === "win32") child.kill();
      else process.kill(-child.pid, "SIGTERM");
    } catch {
      /* already gone */
    }
  };
  const deadline = Date.now() + (server.timeoutMs ?? 120_000);
  while (!(await answers(baseURL))) {
    if (exited !== null) throw new Error(`server command exited with ${exited} before ${baseURL} answered`);
    if (Date.now() > deadline) {
      await stop();
      throw new Error(`${baseURL} did not answer within ${server.timeoutMs ?? 120_000} ms`);
    }
    await new Promise((r) => setTimeout(r, 250));
  }
  return { stop };
}
