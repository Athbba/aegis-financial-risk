#!/usr/bin/env bash
# Official pinned release assets over verified TLS, checked against publisher checksum manifests.
set -euo pipefail
[[ $# == 1 ]] || exit 2
mkdir -p "$1"; destination="$(cd "$1" && pwd)"
[[ "$(uname -m)" == x86_64 ]] || { echo 'Scanner installer supports linux/amd64 only; configure verified native binaries on other hosts.' >&2; exit 2; }
for tool in trivy gitleaks; do
  if [[ "$tool" == trivy ]]; then
    version=0.76.0; repository=aquasecurity/trivy; asset="trivy_${version}_Linux-64bit.tar.gz"; checks="trivy_${version}_checksums.txt"
  else
    version=8.30.1; repository=gitleaks/gitleaks; asset="gitleaks_${version}_linux_x64.tar.gz"; checks="gitleaks_${version}_checksums.txt"
  fi
  curl --fail --silent --show-error --location "https://github.com/$repository/releases/download/v$version/$asset" --output "$destination/$asset"
  curl --fail --silent --show-error --location "https://github.com/$repository/releases/download/v$version/$checks" --output "$destination/$checks"
  (cd "$destination"; awk -v name="$asset" '$2==name { print; found=1 } END { if(!found)exit 1 }' "$checks" | sha256sum --check --strict)
  tar -xzf "$destination/$asset" -C "$destination" "$tool"
done
