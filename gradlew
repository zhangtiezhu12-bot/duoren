#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.7
BOOTSTRAP_DIR="${HOME}/.gradle-bootstrap"
GRADLE_HOME="${BOOTSTRAP_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${BOOTSTRAP_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIST_URL="${GRADLE_DIST_URL:-https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip}"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${BOOTSTRAP_DIR}"
  echo "[VideoCallSDK] Gradle ${GRADLE_VERSION} not found. Downloading once..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "${GRADLE_DIST_URL}" -o "${ZIP_FILE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "${GRADLE_DIST_URL}"
  else
    echo "ERROR: curl or wget is required to bootstrap Gradle." >&2
    exit 1
  fi
  echo "[VideoCallSDK] Extracting Gradle..."
  unzip -q -o "${ZIP_FILE}" -d "${BOOTSTRAP_DIR}"
  rm -f "${ZIP_FILE}"
fi

exec "${GRADLE_HOME}/bin/gradle" "$@"
