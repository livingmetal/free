#!/usr/bin/env bash
set -euo pipefail
umask 077
mkdir -p dist/checks dist/toolchain
BT="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
if [[ -n "${SALARY_KEYSTORE_BASE64:-}" && -n "${SALARY_KEYSTORE_PASSWORD:-}" ]]; then
    printf '%s' "$SALARY_KEYSTORE_BASE64" | base64 -d > "$TMP/salarytimer.p12"
    printf '%s\n' "$SALARY_KEYSTORE_PASSWORD" > "$TMP/password.txt"
elif [[ -f signing/bootstrap-recipient.pem ]]; then
    # One-time bootstrap. Only the owner's public encryption certificate is in Git.
    # The private signing key and its password are never logged or uploaded as plaintext.
    PW="$(openssl rand -hex 32)"
    echo "::add-mask::$PW"
    printf '%s\n' "$PW" > "$TMP/password.txt"
    keytool -genkeypair -noprompt -keystore "$TMP/salarytimer.p12" -storetype PKCS12 \
      -storepass "$PW" -keypass "$PW" -alias salarytimer -keyalg RSA -keysize 3072 \
      -validity 10000 -dname 'CN=Salary Timer Personal, O=Livingmetal'
    tar -C "$TMP" -czf "$TMP/signing-backup.tar.gz" salarytimer.p12 password.txt
    openssl cms -encrypt -binary -aes-256-cbc -in "$TMP/signing-backup.tar.gz" \
      -outform DER -out dist/signing-backup.cms signing/bootstrap-recipient.pem
    unset PW
else
    # Fail closed for distribution: an unsigned build is not an installable update.
    cp app/build/outputs/apk/release/app-release-unsigned.apk dist/UNSIGNED-requires-existing-signing-key.apk
    echo 'No release signing key available. Produced unsigned build only; do not install.'
    echo 'signed=false' >> "$GITHUB_OUTPUT"
    exit 0
fi
cp "$TMP/password.txt" "$TMP/key-password.txt"
"$BT/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$TMP/aligned.apk"
"$BT/apksigner" sign --ks "$TMP/salarytimer.p12" --ks-key-alias salarytimer \
  --ks-pass "file:$TMP/password.txt" --key-pass "file:$TMP/key-password.txt" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --v4-signing-enabled false --out dist/SalaryTimer-v1.3.0.apk "$TMP/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs dist/SalaryTimer-v1.3.0.apk | tee dist/checks/signature.txt
"$BT/zipalign" -c -P 16 -v 4 dist/SalaryTimer-v1.3.0.apk > dist/checks/alignment.txt
"$BT/aapt2" dump badging dist/SalaryTimer-v1.3.0.apk > dist/checks/package.txt
"$BT/aapt2" dump permissions dist/SalaryTimer-v1.3.0.apk > dist/checks/permissions.txt
sha256sum dist/SalaryTimer-v1.3.0.apk > dist/checks/SHA256SUMS
cp "$BT/lib/apksigner.jar" "$BT/zipalign" dist/toolchain/
echo 'signed=true' >> "$GITHUB_OUTPUT"
