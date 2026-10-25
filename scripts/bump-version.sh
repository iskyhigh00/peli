#!/usr/bin/env bash
# Sube la versión como un odómetro de un dígito por campo: 1.0.9 -> 1.1.0, 1.9.9 -> 2.0.0, máximo 9.9.9.
set -euo pipefail
F="$(dirname "$0")/../app/build.gradle.kts"
cur=$(grep -m1 'val appVersion' "$F" | sed -E 's/.*"([0-9.]+)".*/\1/')
IFS=. read -r a b c <<<"$cur"
n=$((a * 100 + b * 10 + c + 1))
if [ "$n" -gt 999 ]; then echo "Ya estás en 9.9.9, no hay versión siguiente." >&2; exit 1; fi
new="$((n / 100)).$(((n / 10) % 10)).$((n % 10))"
sed -i -E "s/(val appVersion = \")[0-9.]+(\")/\1$new\2/" "$F"
echo "$cur -> $new   (versionCode $n). Agrega las novedades de $new en update/Changelog.kt"
