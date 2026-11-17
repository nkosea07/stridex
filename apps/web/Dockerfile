FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.29.2 --activate
COPY . .
RUN pnpm install --frozen-lockfile --filter web... \
    && pnpm contracts:validate \
    && pnpm --filter web build

FROM nginxinc/nginx-unprivileged:1.30-alpine3.24
USER root
RUN apk upgrade --no-cache
USER 101
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/web/dist/client /usr/share/nginx/html
EXPOSE 8080
