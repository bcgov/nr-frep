import { fileURLToPath } from 'node:url';
import { resolve } from 'path';

import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';
import tsconfigPaths from 'vite-tsconfig-paths';
import { configDefaults } from 'vitest/config';

// Per-build id injected into index.html (see the frep-build-id plugin below). Without it, a deploy
// that changes only response headers / infra config (e.g. the CSP in the Caddyfile) leaves index.html
// byte-identical, so its Workbox precache revision is unchanged — the service worker keeps serving the
// stale precached shell (with the OLD header) until users manually clear their cache. Tying the shell
// to a per-commit id busts the shell precache on every deploy, so such changes propagate. Uses the CI
// commit SHA when available (no churn on identical redeploys); falls back to a build timestamp.
const BUILD_ID = process.env.GITHUB_SHA ?? process.env.VITE_BUILD_ID ?? String(Date.now());

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const projectRootDir = fileURLToPath(new URL('.', import.meta.url));
  const define = {
    global: {},
  };
  const devHost = env.VITE_DEV_HOST ?? 'localhost';
  const devPort = Number(env.VITE_DEV_PORT ?? 3000);
  const backendTarget = env.VITE_DEV_BACKEND_TARGET ?? 'http://localhost:8080';
  const hmrPort = env.VITE_HMR_PORT ? Number(env.VITE_HMR_PORT) : devPort;
  const hmrHost = env.VITE_HMR_HOST ?? devHost;
  const hmrProtocolEnv = env.VITE_HMR_PROTOCOL ?? 'ws';
  const hmrProtocol = hmrProtocolEnv === 'wss' ? 'wss' : 'ws';
  return {
    define,
    resolve: {
      alias: {
        '@': resolve(projectRootDir, 'src'),
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          // Carbon's own SCSS trips Sass's mixed-decls and global-builtin deprecations roughly 1200
          // times per build, which buried any warning about our stylesheets. quietDeps silences
          // warnings raised *inside* node_modules only — our own files still report, which is how
          // the dead time-picker rules in _overrides.scss surfaced. Prefer this to
          // silenceDeprecations, which would mute those categories everywhere including our code.
          quietDeps: true,
        },
      },
    },
    plugins: [
      react(),
      tsconfigPaths(),
      // Stamp a build id into index.html so its precache revision changes each deploy — see BUILD_ID.
      {
        name: 'frep-build-id',
        transformIndexHtml() {
          return [
            { tag: 'meta', attrs: { name: 'frep-build', content: BUILD_ID }, injectTo: 'head' },
          ];
        },
      },
      VitePWA({
        registerType: 'autoUpdate',
        // App shell is precached so the CHR editor loads with zero connectivity.
        // Disabled in dev so HMR/dev server is unaffected; active for build/preview.
        devOptions: { enabled: false },
        manifest: {
          name: 'FREP — FRPA Resource Evaluation Program',
          short_name: 'FREP',
          description: 'Forest and Range Evaluation Program checklists',
          theme_color: '#036',
          background_color: '#ffffff',
          display: 'standalone',
          start_url: '/',
          icons: [{ src: '/vite.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
        },
        workbox: {
          navigateFallback: '/index.html',
          globPatterns: ['**/*.{js,css,html,svg,woff,woff2}'],
          // config.js is generated per-container at start-up by docker-entrypoint.sh — it is the
          // ONLY file whose contents differ between environments and deploys. Precaching it froze
          // the runtime config: Workbox fetches a precached URL once at service-worker install and
          // keys it by a build-time revision hash, and that hash comes from the static placeholder
          // in public/config.js, which never changes. So the copy cached on a user's first visit
          // was served forever, and every later deploy's values — backend URL, logout endpoints,
          // support mailbox, allowed attachment types — were silently ignored.
          globIgnores: ['config.js'],
          // The bundled app (Carbon + Amplify) exceeds Workbox's 2 MiB default; raise the
          // precache ceiling so the full app shell is cached for offline field use.
          maximumFileSizeToCacheInBytes: 8 * 1024 * 1024,
          // CHR checklists are persisted in IndexedDB; this just lets read-only
          // GETs resolve from cache when briefly offline.
          runtimeCaching: [
            {
              urlPattern: ({ url }) => url.pathname.startsWith('/api/v1/'),
              method: 'GET',
              handler: 'NetworkFirst',
              options: { cacheName: 'frep-api-get', networkTimeoutSeconds: 5 },
            },
            {
              // Network first, cache as the offline fallback.
              //
              // NOT precached (see globIgnores): a precached config.js is keyed by a build-time
              // revision hash taken from the static placeholder, so it never changes and the copy
              // fetched on a user's first visit is served forever — every later deploy's backend
              // URL, logout endpoints and feature config silently ignored.
              //
              // NOT NetworkOnly either: window.config is defined BY this file, so a failed offline
              // fetch leaves it undefined, env falls back to build-time vars the container image
              // does not carry, and Amplify.configure gets an undefined user pool — breaking the
              // offline CHR editor exactly when it is needed. NetworkFirst gives a fresh config
              // whenever the network answers and the last-known-good one when it does not.
              urlPattern: ({ url }) => url.pathname === '/config.js',
              handler: 'NetworkFirst',
              options: { cacheName: 'frep-runtime-config', networkTimeoutSeconds: 3 },
            },
          ],
        },
      }),
    ],
    base: env.VITE_BASE_PATH || '/',
    build: {
      chunkSizeWarningLimit: 1024,
      outDir: 'dist',
    },
    optimizeDeps: {
      include: [
        '@tanstack/react-query',
        'aws-amplify',
        'aws-amplify/auth/cognito',
        'aws-amplify/utils',
        'react-dom/client',
        'aws-amplify/auth',
      ],
    },
    server: {
      host: devHost,
      port: devPort,
      hmr: {
        overlay: false,
        protocol: hmrProtocol,
        host: hmrHost,
        port: hmrPort,
      },
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    preview: {
      port: devPort,
    },
    test: {
      env,
      globals: true,
      exclude: [...configDefaults.exclude, 'dist/**', 'build/**'],
      coverage: {
        provider: 'v8',
        reporter: ['lcov', 'cobertura', 'html', 'json', 'text'],
        reportsDirectory: './coverage',
        all: true,
        exclude: [
          '**/node_modules/**',
          '**/tests/**',
          '**/*.test.{ts,tsx}',
          '**/vite-env.d.ts',
          '**/types/**',
          '**/constants/**',
          '**/config/fam/*',
          '**/config/react-query/*',
          '**/config/tests/*',
          '**/*.env.ts',
          '**/*.scss',
          '**/*.css',
          '**/*.d.ts',
          '**/types.ts',
          '**/main.tsx',
          '**/App.tsx',
        ],
        include: ['src/**/*.ts', 'src/**/*.tsx'],
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
      },
      projects: [
        {
          resolve: {
            alias: {
              '@': resolve(projectRootDir, 'src'),
            },
          },
          plugins: [react(), tsconfigPaths()],
          // Vitest projects do not inherit the root-level `css` option; without this the Carbon
          // deprecation warnings silenced for the build reappear on every test run.
          css: { preprocessorOptions: { scss: { quietDeps: true } } },
          test: {
            name: 'node',
            setupFiles: [
              './src/config/tests/setup-env.ts',
              './src/config/tests/custom-matchers.ts',
            ],
            environment: 'happy-dom',
            include: ['src/**/*.unit.test.{ts,tsx}'],
          },
        },
        {
          resolve: {
            alias: {
              '@': resolve(projectRootDir, 'src'),
            },
          },
          plugins: [react(), tsconfigPaths()],
          // Vitest projects do not inherit the root-level `css` option; without this the Carbon
          // deprecation warnings silenced for the build reappear on every test run.
          css: { preprocessorOptions: { scss: { quietDeps: true } } },
          test: {
            name: 'browser',
            setupFiles: [
              './src/config/tests/setup-browser.ts',
              './src/config/tests/custom-matchers.ts',
            ],
            browser: {
              enabled: true,
              provider: 'playwright',
              instances: [{ browser: 'chromium' }],
            },
            include: ['src/**/*.browser.test.{ts,tsx}'],
          },
        },
      ],
    },
  };
});
