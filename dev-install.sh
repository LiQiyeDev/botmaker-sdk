#!/usr/bin/env bash
#
# dev-install.sh — install this SDK build into your local ~/.m2 so a generated bot picks up local
# changes WITHOUT pushing a git tag.
#
# Why this exists: a generated bot pins `com.github.LiQiyeDev:botmaker-sdk:<version>`, and Maven checks
# your local ~/.m2 before JitPack. A plain `mvn install` now works correctly (groupId matches), but
# this script also allows you to use a custom dev version label (e.g. `local-SNAPSHOT` instead of
# the default `0.0.0-SNAPSHOT`) and ensures shared is pinned to 0.0.0-SNAPSHOT for local development.
#
# Usage:
#   ./dev-install.sh                 # install as com.github.LiQiyeDev:botmaker-sdk:local-SNAPSHOT
#   DEV_SDK_VERSION=mine ./dev-install.sh   # override the dev version label
#
# Then, in Studio, pick the dev version (default `local-SNAPSHOT`) from the SDK version dropdown — Studio
# auto-lists locally-installed SNAPSHOT builds at the top (New Project and Manage Libraries), so there's
# nothing to type. Your bot resolves the local build first; JitPack stays the fallback. Users select real
# released versions and never have this in their ~/.m2, so they are unaffected.

set -euo pipefail

SDK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UMBRELLA="$(dirname "$SDK_DIR")"
DEV_VERSION="${DEV_SDK_VERSION:-local-SNAPSHOT}"

[[ -f "$UMBRELLA/pom.xml" ]] || {
  echo "error: expected the umbrella pom at $UMBRELLA/pom.xml (run from the umbrella checkout)" >&2
  exit 1
}

echo "==> installing botmaker-shared into ~/.m2"
mvn -q -f "$UMBRELLA/botmaker-shared/pom.xml" install -DskipTests

echo "==> installing botmaker-sdk as com.github.LiQiyeDev:botmaker-sdk:$DEV_VERSION"
cp "$SDK_DIR/pom.xml" "$SDK_DIR/pom.xml.devbak"
trap 'mv -f "$SDK_DIR/pom.xml.devbak" "$SDK_DIR/pom.xml"' EXIT

# Only need to change the version now (groupId is already correct in pom.xml)
sed -i "s#<version>0.0.0-SNAPSHOT</version>#<version>${DEV_VERSION}</version>#" "$SDK_DIR/pom.xml"

# Point the shared dep at the local reactor SNAPSHOT (installed just above), regardless of what the
# committed pom pins botmaker.shared.version to — after a release it's a real tag (e.g. v0.0.2), which
# would make this dev build pull shared from JitPack instead of your freshly dev-installed local build.
# Restored with the rest of the pom by the EXIT trap.
sed -i 's#<botmaker.shared.version>[^<]*</botmaker.shared.version>#<botmaker.shared.version>0.0.0-SNAPSHOT</botmaker.shared.version>#' "$SDK_DIR/pom.xml"

mvn -q -f "$SDK_DIR/pom.xml" install -DskipTests   # installs the jar + attached sources jar

echo
echo "Done. In Studio, pick '$DEV_VERSION' from the SDK version dropdown — it's auto-listed at the top"
echo "when a local build is installed (New Project, or Project > Manage Libraries), so no typing needed."
echo "(installed at ~/.m2/repository/com/github/LiQiyeDev/botmaker-sdk/$DEV_VERSION/)"
