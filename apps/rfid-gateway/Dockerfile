FROM node:22-alpine
ENV NODE_ENV=production \
    RFID_GATEWAY_PORT=8090 \
    RFID_GATEWAY_SPOOL_DIR=/var/lib/rfid-gateway
WORKDIR /app
COPY --chown=node:node package.json ./
COPY --chown=node:node src ./src
RUN mkdir -p /var/lib/rfid-gateway && chown -R node:node /var/lib/rfid-gateway
USER node
VOLUME ["/var/lib/rfid-gateway"]
EXPOSE 8090
CMD ["node", "src/index.mjs"]
