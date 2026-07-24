# The gate container for this repository.
#
# FROM the litter-box base image, which carries a JDK, the pinned Claude CLI and a non-root `gate`
# user, and NO credentials of any kind. It deliberately carries no build tool: that is this file's
# job, and it is the only part of the sandbox that knows what this project is built with. See
# https://github.com/rcardin/litter-box/blob/main/docs/base-image.md for the contract the base
# image guarantees.
#
# No ENTRYPOINT. The three runners (gate, agent, reviewer) all override it, because the command to
# run is a property of the RUN — `gate.fast` from .litter-box/config.conf, or the agent entrypoint
# script — not of the image. An ENTRYPOINT here would be dead weight at best and a build tool
# hardcoded in two places at worst.
#
# BASE_IMAGE is overridden to a locally built tag when litter-box builds this file, so a run never
# needs the registry; the default below is what a hand-run `docker build` gets.
ARG BASE_IMAGE=ghcr.io/rcardin/litter-box-base:0.1.0
FROM ${BASE_IMAGE}

USER root

# This project compiles with `-release:25` (build.sbt), and the litter-box base image ships
# temurin 21. scalac cannot emit a release the JDK it runs on does not know, so the gate needs a
# JDK 25 of its own. sbt runs scalac in-process, so it is sbt's JVM that has to be 25 — hence
# JAVA_HOME and PATH below, not just an extra JDK on disk.
#
# Downloaded at BUILD time, which is unproxied; .litter-box/allowlist governs the gate container's
# RUNTIME egress only and needs no entry for this.
ARG GATE_JDK_VERSION=25
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64)        adopt_arch=x64 ;; \
      aarch64|arm64) adopt_arch=aarch64 ;; \
      *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;; \
    esac; \
    curl -fsSL \
      "https://api.adoptium.net/v3/binary/latest/${GATE_JDK_VERSION}/ga/linux/${adopt_arch}/jdk/hotspot/normal/eclipse?project=jdk" \
      -o /tmp/jdk.tgz; \
    mkdir -p /opt/jdk; \
    tar -xzf /tmp/jdk.tgz -C /opt/jdk --strip-components=1; \
    rm /tmp/jdk.tgz
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:$PATH

ARG SBT_VERSION=1.12.9
RUN curl -fsSL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" \
      -o /tmp/sbt.tgz \
    && tar -xzf /tmp/sbt.tgz -C /usr/local \
    && rm /tmp/sbt.tgz \
    && ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt
USER gate
WORKDIR /workspace