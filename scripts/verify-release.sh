#!/usr/bin/env bash
# Verifies signed release artifacts.
#
#   scripts/verify-release.sh <app-release.apk> <app-release.aab> <keystore.p12>
#
# Needs: ANDROID_HOME (build-tools 36.0.0), JDK (keytool, jarsigner), unzip, and
# ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS in the environment.
# Never prints passwords. Writes a report to build/verification/report.md.
set -euo pipefail

APK="$1"
AAB="$2"
KEYSTORE="$3"
: "${ANDROID_HOME:?ANDROID_HOME must be set}"
: "${ANDROID_KEYSTORE_PASSWORD:?missing}"
: "${ANDROID_KEY_ALIAS:?missing}"

BUILD_TOOLS="${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
APKSIGNER="${BUILD_TOOLS}/apksigner"
AAPT2="${BUILD_TOOLS}/aapt2"
ZIPALIGN="${BUILD_TOOLS}/zipalign"
OUT="build/verification"
mkdir -p "$OUT"
REPORT="$OUT/report.md"
: > "$REPORT"
log() { echo "$*" | tee -a "$REPORT"; }
fail() { log "FAIL: $*"; exit 1; }

log "# Release verification"
log ""
log "- APK: \`$APK\`"
log "- AAB: \`$AAB\`"
log ""

# ---------------------------------------------------------------- expected signer
EXPECTED_SHA256=$(keytool -list -v -storetype PKCS12 -keystore "$KEYSTORE" \
    -storepass:env ANDROID_KEYSTORE_PASSWORD -alias "$ANDROID_KEY_ALIAS" 2>/dev/null \
  | awk -F': ' '/SHA256:/ {print $2; exit}' | tr -d ':' | tr 'A-F' 'a-f' | tr -d ' ')
[ -n "$EXPECTED_SHA256" ] || fail "could not read the release certificate from the keystore"
EXPECTED_OWNER=$(keytool -list -v -storetype PKCS12 -keystore "$KEYSTORE" \
    -storepass:env ANDROID_KEYSTORE_PASSWORD -alias "$ANDROID_KEY_ALIAS" 2>/dev/null \
  | awk -F': ' '/^Owner:/ {print $2; exit}')
log "## Expected signer"
log "- Owner: $EXPECTED_OWNER"
log "- SHA-256: $EXPECTED_SHA256"
case "$EXPECTED_OWNER" in *"CN=Android Debug"*) fail "keystore holds the debug certificate";; esac
log ""

# ---------------------------------------------------------------- APK signature
log "## APK signature (apksigner verify --print-certs)"
"$APKSIGNER" verify --verbose --print-certs "$APK" > "$OUT/apksigner.txt" 2>&1 \
  || { cat "$OUT/apksigner.txt"; fail "apksigner verify failed"; }
grep -E "Verified using|Signer #1 certificate (DN|SHA-256)" "$OUT/apksigner.txt" | tee -a "$REPORT"
grep -q "CN=Android Debug" "$OUT/apksigner.txt" && fail "APK is signed with the Android Debug certificate"
APK_SHA256=$(awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}' "$OUT/apksigner.txt" | tr -d ' ')
[ "$APK_SHA256" = "$EXPECTED_SHA256" ] || fail "APK signer $APK_SHA256 does not match release key $EXPECTED_SHA256"
log "- APK signer matches the release key."
log ""

# ---------------------------------------------------------------- AAB signature
# AABs are JAR-signed. A self-signed upload certificate is normal and is not rejected.
log "## AAB signature (jarsigner -verify)"
jarsigner -verify -verbose -certs "$AAB" > "$OUT/jarsigner.txt" 2>&1 || true
grep -q "jar verified" "$OUT/jarsigner.txt" || { tail -20 "$OUT/jarsigner.txt"; fail "AAB JAR signature did not verify"; }
grep -q "CN=Android Debug" "$OUT/jarsigner.txt" && fail "AAB is signed with the Android Debug certificate"
SIG_BLOCK=$(unzip -Z1 "$AAB" | grep -E '^META-INF/[^/]+\.(RSA|EC|DSA)$' | head -1)
[ -n "$SIG_BLOCK" ] || fail "no signature block in AAB"
unzip -p "$AAB" "$SIG_BLOCK" > "$OUT/aab-signature-block"
AAB_SHA256=$(keytool -printcert -file "$OUT/aab-signature-block" 2>/dev/null \
  | awk -F': ' '/SHA256:/ {print $2; exit}' | tr -d ':' | tr 'A-F' 'a-f' | tr -d ' ')
[ "$AAB_SHA256" = "$EXPECTED_SHA256" ] || fail "AAB signer $AAB_SHA256 does not match release key $EXPECTED_SHA256"
log "- jar verified; AAB signer matches the release key ($SIG_BLOCK)."
log ""

# ---------------------------------------------------------------- permissions
log "## Release permissions (packaged APK manifest)"
"$AAPT2" dump permissions "$APK" > "$OUT/permissions.txt"
cat "$OUT/permissions.txt" | tee -a "$REPORT"
PACKAGE=$("$AAPT2" dump packagename "$APK")
# The only permission allowed is androidx.core's app-private signature permission
# used for non-exported dynamic receivers. It is not a runtime permission.
UNEXPECTED=$(grep -E "^uses-permission" "$OUT/permissions.txt" \
  | grep -v "name='${PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'" || true)
if [ -n "$UNEXPECTED" ]; then
  log "Unexpected permissions:"; log "$UNEXPECTED"
  fail "release manifest requests unexpected permissions"
fi
grep -Eq "INTERNET|ACCESS_NETWORK_STATE" "$OUT/permissions.txt" && fail "network permission present"
log "- No INTERNET, ACCESS_NETWORK_STATE or other unexpected permissions."
log ""

# ---------------------------------------------------------------- backup flags
log "## Backup configuration"
"$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" > "$OUT/manifest-tree.txt"
grep -E "allowBackup|dataExtractionRules|fullBackupContent" "$OUT/manifest-tree.txt" | tee -a "$REPORT"
grep -Eq 'allowBackup\(0x[0-9a-f]+\)=(false|0x0)' "$OUT/manifest-tree.txt" || fail "allowBackup is not false"
grep -q "dataExtractionRules" "$OUT/manifest-tree.txt" || fail "dataExtractionRules missing"
log ""

# ---------------------------------------------------------------- native libraries / 16 KB
log "## Native libraries and 16 KB page size"
APK_SO=$(unzip -Z1 "$APK" | grep -E '\.so$' || true)
AAB_SO=$(unzip -Z1 "$AAB" | grep -E '\.so$' || true)
if [ -z "$APK_SO" ] && [ -z "$AAB_SO" ]; then
  log "- No native .so libraries are packaged in the APK or the AAB (including transitive dependencies)."
  log "- 16 KB ELF alignment checks are therefore not applicable; zipalign -P 16 still run below."
else
  log "Native libraries found:"
  log "APK:"; log "${APK_SO:-none}"
  log "AAB:"; log "${AAB_SO:-none}"
  mkdir -p "$OUT/so"
  unzip -o -q "$APK" 'lib/*' -d "$OUT/so" || true
  BAD=0
  while IFS= read -r so; do
    [ -f "$OUT/so/$so" ] || continue
    ALIGNS=$(readelf -lW "$OUT/so/$so" | awk '$1=="LOAD" {print $NF}')
    for a in $ALIGNS; do
      if [ $((a)) -lt $((0x4000)) ]; then log "  $so LOAD align $a < 16 KB"; BAD=1; fi
    done
  done <<< "$APK_SO"
  [ "$BAD" = 0 ] || fail "native library ELF segments are not 16 KB aligned"
  log "- All LOAD segments are aligned to at least 16 KB."
fi
"$ZIPALIGN" -c -P 16 -v 4 "$APK" > "$OUT/zipalign.txt" 2>&1 || { tail -20 "$OUT/zipalign.txt"; fail "zipalign -P 16 check failed"; }
log "- zipalign -c -P 16 -v 4: $(tail -1 "$OUT/zipalign.txt")"
log ""
log "All release checks passed."
