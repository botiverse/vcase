import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  // The sample shares its font files with samples/compose-desktop.
  server: { fs: { allow: [".", "../compose-desktop/src/main/resources/fonts"] } },
  preview: { host: "127.0.0.1", port: 4173, strictPort: true },
});
