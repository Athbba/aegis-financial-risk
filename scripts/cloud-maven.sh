#!/usr/bin/env bash
# Optional cloud helper; normal developer machines and CI can run mvn directly from backend/.
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -d /workspace/.tools/jdk-21 ]]; then export JAVA_HOME=/workspace/.tools/jdk-21; fi
maven_command="${AEGIS_MAVEN_COMMAND:-mvn}"
if [[ -x /workspace/.tools/apache-maven-3.9.11/bin/mvn ]]; then
  maven_command=/workspace/.tools/apache-maven-3.9.11/bin/mvn
fi
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
settings_file="$(mktemp /tmp/aegis-maven-settings.XXXXXX)"
trap 'rm -f "$settings_file"' EXIT
printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' > "$settings_file"
environment_proxy="${HTTPS_PROXY:-${https_proxy:-}}"
if [[ -n "$environment_proxy" ]]; then
  proxy_protocol="${environment_proxy%%://*}"
  proxy_authority="${environment_proxy#*://}"
  proxy_authority="${proxy_authority%%/*}"
  # Use proxy routing only; do not extract or persist proxy credentials.
  proxy_authority="${proxy_authority##*@}"
  if [[ "$proxy_protocol" != http && "$proxy_protocol" != https ]] ||
     [[ ! "$proxy_authority" =~ ^([A-Za-z0-9.-]+)(:([0-9]+))?$ ]]; then
    echo 'Unsupported proxy address; configure Maven proxy settings explicitly.' >&2
    exit 1
  fi
  proxy_hostname="${BASH_REMATCH[1]}"
  proxy_port="${BASH_REMATCH[3]:-80}"
  if [[ "$proxy_protocol" == https && -z "${BASH_REMATCH[3]}" ]]; then proxy_port=443; fi
  printf '<proxies><proxy><id>cloud-egress</id><active>true</active><protocol>%s</protocol><host>%s</host><port>%s</port><nonProxyHosts>localhost|127.*</nonProxyHosts></proxy></proxies>' \
    "$proxy_protocol" "$proxy_hostname" "$proxy_port" >> "$settings_file"
fi
printf '</settings>' >> "$settings_file"
"$maven_command" -s "$settings_file" -Dmaven.repo.local="${AEGIS_MAVEN_CACHE:-/workspace/.cache/m2}" -f "$repo_dir/backend/pom.xml" "$@"
