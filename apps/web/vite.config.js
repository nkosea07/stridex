var __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
import { defineConfig } from 'vite';
import { reactRouter } from '@react-router/dev/vite';
import { fileURLToPath } from 'node:url';
var srcDir = fileURLToPath(new URL('./src', import.meta.url));
export default defineConfig({
    plugins: __spreadArray([], reactRouter(), true),
    resolve: {
        alias: {
            '@': srcDir,
        },
    },
    optimizeDeps: {
        include: ['cookie'],
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8081',
                changeOrigin: true,
                ws: true,
            },
        },
    },
});
