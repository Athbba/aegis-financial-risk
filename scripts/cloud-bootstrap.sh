#!/usr/bin/env bash
# Optional preparation for this cloud workspace; normal local development uses installed JDK/Maven.
set -euo pipefail
mkdir -p /workspace/.tools /workspace/.cache
if [[ ! -x /workspace/.tools/jdk-21/bin/javac ]]; then
  if [[ -e /workspace/.tools/jdk-21 ]]; then
    echo 'Existing incomplete JDK directory preserved. Repair it explicitly before continuing.' >&2
    exit 1
  fi
  docker pull eclipse-temurin:21-jdk
  jdk_container="$(docker create eclipse-temurin:21-jdk)"
  trap 'docker rm "$jdk_container" >/dev/null' EXIT
  docker cp "$jdk_container:/opt/java/openjdk" /workspace/.tools/jdk-21
  docker rm "$jdk_container" >/dev/null
  trap - EXIT
fi
if [[ ! -x /workspace/.tools/apache-maven-3.9.11/bin/mvn ]]; then
  download_dir="$(mktemp -d /tmp/aegis-maven-download.XXXXXX)"
  trap 'rm -rf "$download_dir"' EXIT
  base_url=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11
  curl --fail --silent --show-error --location "$base_url/apache-maven-3.9.11-bin.tar.gz" -o "$download_dir/maven.tar.gz"
  curl --fail --silent --show-error --location "$base_url/apache-maven-3.9.11-bin.tar.gz.sha512" -o "$download_dir/maven.tar.gz.sha512"
  expected_digest="$(tr -d '[:space:]' < "$download_dir/maven.tar.gz.sha512")"
  actual_digest="$(sha512sum "$download_dir/maven.tar.gz")"
  actual_digest="${actual_digest%% *}"
  if [[ "$actual_digest" != "$expected_digest" ]]; then
    echo 'Maven SHA-512 verification failed; download will not be used.' >&2
    exit 1
  fi
  tar -xzf "$download_dir/maven.tar.gz" -C /workspace/.tools
  rm -rf "$download_dir"
  trap - EXIT
fi
/workspace/.tools/jdk-21/bin/javac -version
JAVA_HOME=/workspace/.tools/jdk-21 /workspace/.tools/apache-maven-3.9.11/bin/mvn -version
