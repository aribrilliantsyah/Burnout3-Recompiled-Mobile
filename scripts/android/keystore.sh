#!/usr/bin/env bash
# Creates the release signing key, once.
#
#   scripts/android/keystore.sh
#
# Writes ~/.android-keys/b3x-release.jks and android/keystore.properties
# (git-ignored). keytool asks for the passwords in this terminal; they end
# up only in keystore.properties on this machine.
#
# Keep a backup of the .jks and its password: every update of the app has
# to be signed with this same key, or phones refuse to install it over the
# old one.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="${B3X_KEY_DIR:-$HOME/.android-keys}"
JKS="$DIR/b3x-release.jks"
PROPS="$HERE/android/keystore.properties"
ALIAS=b3x

if [ -f "$JKS" ]; then
    echo "[keystore] $JKS already exists; not replacing it."
else
    mkdir -p "$DIR"
    chmod 700 "$DIR"
    echo "[keystore] creating $JKS (choose a password, at least 6 characters)"
    keytool -genkeypair -v -keystore "$JKS" -alias "$ALIAS" \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=b3x, O=ryuna, C=ID"
    chmod 600 "$JKS"
fi

if [ -f "$PROPS" ]; then
    echo "[keystore] $PROPS already exists."
    exit 0
fi

read -rsp "Keystore password (for keystore.properties): " PASS; echo
umask 077
cat > "$PROPS" <<EOF
storeFile=$JKS
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
EOF
echo "[keystore] wrote $PROPS (git-ignored). Back up $JKS and the password."
