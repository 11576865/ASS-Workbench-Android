#!/usr/bin/env bash
set -euo pipefail

root="${1:-.}"
cd "$root"

test -f buildscripts/include/depinfo.sh
test -f buildscripts/download-deps.sh
test -f buildscripts/scripts/libass.sh

python3 - <<'PY'
from pathlib import Path

dep = Path("buildscripts/include/depinfo.sh")
s = dep.read_text()
s = s.replace(
    "v_mbedtls=3.6.7\n",
    "v_mbedtls=3.6.7\nv_libxml2=2.15.4\nv_fontconfig=2.18.3\n",
)
s = s.replace(
    "dep_freetype2=()\n",
    "dep_freetype2=()\ndep_libxml2=()\ndep_fontconfig=(libxml2 freetype2)\n",
)
s = s.replace(
    "dep_libass=(freetype2 fribidi harfbuzz unibreak)",
    "dep_libass=(freetype2 fontconfig fribidi harfbuzz unibreak)",
)
s = s.replace(
    "-mbedtls${v_mbedtls}-harfbuzz",
    "-mbedtls${v_mbedtls}-libxml2${v_libxml2}-fontconfig${v_fontconfig}-harfbuzz",
)
dep.write_text(s)

dl = Path("buildscripts/download-deps.sh")
s = dl.read_text()
anchor = 'clone freetype2  https://gitlab.freedesktop.org/freetype/freetype.git "VER-${v_freetype//./-}" --recurse-submodules --shallow-submodules\n'
insert = anchor + '''fetch_tar libxml2 "https://gitlab.gnome.org/GNOME/libxml2/-/archive/v${v_libxml2}/libxml2-v${v_libxml2}.tar.gz"
fetch_tar fontconfig "https://gitlab.freedesktop.org/fontconfig/fontconfig/-/archive/${v_fontconfig}/fontconfig-${v_fontconfig}.tar.gz"
'''
if anchor not in s:
    raise SystemExit("download-deps anchor not found")
s = s.replace(anchor, insert)
dl.write_text(s)

libass = Path("buildscripts/scripts/libass.sh")
s = libass.read_text()
s = s.replace(
    "--enable-libunibreak --disable-require-system-font-provider",
    "--enable-libunibreak --enable-fontconfig",
)
libass.write_text(s)

for path in Path(".").glob("libmpvkt*/build.gradle.kts"):
    text = path.read_text()
    if "compileSdk = 37" in text:
        path.write_text(text.replace("compileSdk = 37", "compileSdk = 36"))
PY

cat > buildscripts/scripts/libxml2.sh <<'EOF'
#!/bin/bash -e
set -eo pipefail

. ../../include/path.sh
build=_build$ndk_suffix

if [ "$1" == "build" ]; then
    true
elif [ "$1" == "clean" ]; then
    rm -rf "$build"
    exit 0
else
    exit 255
fi

unset CC CXX
meson setup "$build" --cross-file "$prefix_dir"/crossfile.txt \
    -Dminimum=true -D{push,reader,sax1,iso8859x,pattern}=enabled

ninja -C "$build" -j$cores
DESTDIR="$prefix_dir" ninja -C "$build" install
EOF

cat > buildscripts/scripts/fontconfig.sh <<'EOF'
#!/bin/bash -e
set -eo pipefail

. ../../include/path.sh
build=_build$ndk_suffix

if [ "$1" == "build" ]; then
    true
elif [ "$1" == "clean" ]; then
    rm -rf "$build"
    exit 0
else
    exit 255
fi

unset CC CXX
meson setup "$build" --cross-file "$prefix_dir"/crossfile.txt \
    -D{tests,doc,tools,nls}=disabled \
    -Dxml-backend=libxml2

ninja -C "$build" -j$cores
DESTDIR="$prefix_dir" ninja -C "$build" install
EOF

chmod +x buildscripts/scripts/libxml2.sh buildscripts/scripts/fontconfig.sh

grep -q 'v_fontconfig=2.18.3' buildscripts/include/depinfo.sh
grep -q 'dep_libass=(freetype2 fontconfig fribidi harfbuzz unibreak)' buildscripts/include/depinfo.sh
grep -q -- '--enable-libunibreak --enable-fontconfig' buildscripts/scripts/libass.sh
! grep -R --include='build.gradle.kts' -q 'compileSdk = 37' libmpvkt*

echo "libmpvKt fontconfig patch applied"
