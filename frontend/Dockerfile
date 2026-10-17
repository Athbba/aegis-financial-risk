FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime-base
COPY --chown=nginx:nginx --chmod=644 nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080

# Optional offline packaging path after a successful local `npm run build`.
FROM runtime-base AS prebuilt
COPY --chown=nginx:nginx dist /usr/share/nginx/html

FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM runtime-base AS final
COPY --from=build --chown=nginx:nginx /build/dist /usr/share/nginx/html
