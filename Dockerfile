FROM docker.io/library/node:26-alpine AS build
WORKDIR /app
ARG VITE_SOURCE_URL=https://github.com/sppidy/mise
ENV VITE_SOURCE_URL=$VITE_SOURCE_URL
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM docker.io/library/node:26-alpine AS runtime
WORKDIR /app
LABEL org.opencontainers.image.source="https://github.com/sppidy/mise" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"
ENV NODE_ENV=production \
    PORT=8787 \
    DATA_DIR=/app/data
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
COPY server ./server
COPY shared ./shared
COPY LICENSE LICENSE-EXCEPTION.md NOTICE PRIVACY.md THIRD_PARTY_NOTICES.md ./
COPY THIRD_PARTY_LICENSES ./THIRD_PARTY_LICENSES
RUN mkdir -p /app/data && chown -R node:node /app
USER node
EXPOSE 8787
VOLUME ["/app/data"]
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD wget -qO- http://127.0.0.1:8787/api/health >/dev/null || exit 1
CMD ["node", "server/index.mjs"]
