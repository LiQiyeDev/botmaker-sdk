#!/usr/bin/env bash
#
# dev-install.sh — install this SDK build into your local ~/.m2 so a generated bot picks up local
# changes WITHOUT pushing a git tag.
#
# Why this exists: a generated bot pins `com.github.LiQiyeDev:botmaker-sdk:<version>`, and Maven checks
# your local ~/.m2 before JitPack. But a plain `mvn install` here installs under `com.botmaker.sdk:...`
# — the wrong coordinate — so a bot never sees it. This script installs the SDK (and the shared module
# it depends on) under the *JitPack* coordinate with a `local-SNAPSHOT` dev version instead.
#
# Usage:
#   ./dev-install.sh                 # install as com.github.LiQiyeDev:botmaker-sdk:local-SNAPSHOT
#   DEV_SDK_VERSION=mine ./dev-install.sh   # override the dev version label
#
# Then, in Studio's project screen, type the dev version (default `local-SNAPSHOT`) into the SDK
# version field. Your bot resolves the local build first; JitPack stays the fallback. Users select real
# released versions and never have this in their ~/.m2, so they are unaffected.

set -euo pipefail

SDK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UMBRELLA="$(dirname "$SDK_DIR")"
DEV_VERSION="${DEV_SDK_VERSION:-local-SNAPSHOT}"

[[ -f "$UMBRELLA/pom.xml" ]] || {
  echo "error: expected the umbrella pom at $UMBRELLA/pom.xml (run from the umbrella checkout)" >&2
  exit 1
}

echo "==> (re)installing botmaker-shared into ~/.m2 (so the SDK build + bots resolve it)"
mvn -q -f "$UMBRELLA/pom.xml" -pl botmaker-shared install -DskipTests

echo "==> installing botmaker-sdk as com.github.LiQiyeDev:botmaker-sdk:$DEV_VERSION"
cp "$SDK_DIR/pom.xml" "$SDK_DIR/pom.xml.devbak"
trap 'mv -f "$SDK_DIR/pom.xml.devbak" "$SDK_DIR/pom.xml"' EXIT

# Rewrite ONLY the project's own coordinates. The shared dep uses ${botmaker.shared.version} and the
# property line has a different tag name, so these exact-match seds never touch them.
sed -i 's#<groupId>com.botmaker.sdk</groupId>#<groupId>com.github.LiQiyeDev</groupId>#' "$SDK_DIR/pom.xml"
sed -i "s#<version>0.0.0-SNAPSHOT</version>#<version>${DEV_VERSION}</version>#"        "$SDK_DIR/pom.xml"

mvn -q -f "$SDK_DIR/pom.xml" install -DskipTests   # installs the jar + attached sources jar

echo
echo "Done. In Studio's SDK version field enter:  $DEV_VERSION"
echo "(installed at ~/.m2/repository/com/github/LiQiyeDev/botmaker-sdk/$DEV_VERSION/)"
