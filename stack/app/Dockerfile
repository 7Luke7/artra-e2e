# syntax=docker/dockerfile:1
#
# The application under test.
#
# Kept in the test repository rather than in Artra's own: the E2E stack owns
# how the app is packaged *for testing*, and the application repository should
# not have to carry a Dockerfile it does not otherwise need. scripts/prepare-app.sh
# stages the application source next to this file before the build.
#
# Two stages so the runtime image carries the built server and its production
# dependencies, not the toolchain that produced them.

FROM node:24-bookworm-slim AS build

WORKDIR /app

# Dependencies first: this layer is only rebuilt when the manifests change, so
# ordinary source edits rebuild in seconds instead of re-resolving the tree.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_* values are inlined into the client bundle at build time, so the URL the
# browsers will use has to be known here - it cannot be injected at run time
# like the server-side settings. That is why the compose file passes them as
# build args.
ARG VITE_URL=https://artra.test
ARG VITE_WS_URL=wss://artra.test/ws
ARG VITE_EMAIL=support@artra.test
ARG VITE_GOOGLE_CLIENT_ID=""
ENV VITE_URL=$VITE_URL \
    VITE_WS_URL=$VITE_WS_URL \
    VITE_EMAIL=$VITE_EMAIL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID

RUN npm run build


# One-shot fixture seeder. It reuses the build stage because it needs the pg
# driver from the full dependency tree, which the runtime image does not carry
# as an importable module.
FROM build AS seed
CMD ["node", "seed-users.mjs"]


FROM node:24-bookworm-slim AS runtime

# curl is the container's own healthcheck; compose gates the test runner on it,
# so the suite never starts against a server that is still booting.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOST=0.0.0.0

COPY --from=build /app/.output ./.output

EXPOSE 3000

HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=20 \
    CMD curl -fsS http://127.0.0.1:3000/ > /dev/null || exit 1

CMD ["node", ".output/server/index.mjs"]
