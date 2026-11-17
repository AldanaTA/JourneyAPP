import { fileURLToPath } from "node:url"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

const srcDir = fileURLToPath(new URL("./src", import.meta.url))

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": srcDir,
      components: fileURLToPath(new URL("./src/components", import.meta.url)),
      ui: fileURLToPath(new URL("./src/components/ui", import.meta.url)),
      lib: fileURLToPath(new URL("./src/lib", import.meta.url)),
      hooks: fileURLToPath(new URL("./src/hooks", import.meta.url)),
      utils: fileURLToPath(new URL("./src/lib", import.meta.url)),
    },
  },
})