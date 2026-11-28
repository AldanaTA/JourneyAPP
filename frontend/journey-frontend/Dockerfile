# Builds the React app, then serves the static files with nginx (port 80).
# In the full stack Caddy sends /api/* to the API and everything else here, so the app and API share one domain.

FROM node:22-alpine AS build
WORKDIR /app

# Cap the build's memory (tsc + vite); raise it with --build-arg NODE_MAX_OLD_SPACE=... on a bigger machine.
ARG NODE_MAX_OLD_SPACE=1024
ENV NODE_OPTIONS=--max-old-space-size=${NODE_MAX_OLD_SPACE}

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Where the app sends API calls. The default (/api/v1 on the same domain) is right behind Caddy.
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
