FROM postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
ARG SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.revision=$SOURCE_COMMIT
# Official entrypoint calls gosu only when uid=0. This image initializes/runs as postgres.
# New Docker volumes inherit the publisher's postgres-owned data directory. No root phase.
USER root
RUN rm /usr/local/bin/gosu
USER postgres
