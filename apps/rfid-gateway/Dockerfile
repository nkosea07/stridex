FROM node:26-alpine
ENV NODE_ENV=production \
    RFID_GATEWAY_PORT=8090 \
    RFID_GATEWAY_HOST=0.0.0.0 \
    RFID_GATEWAY_SPOOL_DIR=/var/lib/rfid-gateway
RUN apk upgrade --no-cache \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
       /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack /opt/yarn-* /usr/local/bin/yarn /usr/local/bin/yarnpkg
WORKDIR /app
COPY --chown=node:node package.json ./
COPY --chown=node:node src ./src
RUN mkdir -p /var/lib/rfid-gateway && chown -R node:node /var/lib/rfid-gateway
USER node
VOLUME ["/var/lib/rfid-gateway"]
EXPOSE 8090
CMD ["node", "src/index.mjs"]
