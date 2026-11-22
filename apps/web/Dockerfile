FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.29.2 --activate
COPY . .
RUN pnpm install --frozen-lockfile --filter web... \
    && pnpm contracts:validate \
    && pnpm --filter web build

FROM nginxinc/nginx-unprivileged:1.31-alpine3.24-slim
USER root
RUN apk upgrade --no-cache \
    && apk add --no-cache --repositories-file /dev/null \
        -X "https://nginx.org/packages/alpine/v$(grep -oE '^[0-9]+\.[0-9]+' /etc/alpine-release)/main" \
        'nginx>=1.30.7-r1' \
    && rm -f /etc/nginx/*.apk-new /etc/nginx/conf.d/*.apk-new \
    && chown -R 101:0 /var/cache/nginx /etc/nginx \
    && chmod -R g+w /var/cache/nginx /etc/nginx
USER 101
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=0755 apps/web/docker/40-app-links.sh /docker-entrypoint.d/40-app-links.sh
COPY --from=build /workspace/apps/web/dist/client /usr/share/nginx/html
EXPOSE 8080
