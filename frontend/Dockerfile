FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:782bb89768cc87b7f95c18b95baa1af250e5d451f26794f59363540161147c01 AS runtime-base
ARG SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.revision=$SOURCE_COMMIT
# Neither configuration uses image_filter or loads its optional module.
USER root
RUN apk --no-network del nginx-module-image-filter
USER nginx
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
