#!/usr/bin/env bash
# Builds playa_<version>_amd64.deb: the app with its own Java under /opt/playa, a launcher,
# icons, and "Open with Playa" for video files. apt installs what it needs from Ubuntu:
#   sudo apt install ./build/playa_<version>_amd64.deb
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/Android/jdk}"

./gradlew -q icon createDistributable
version=$(./gradlew -q printVersion)
app=build/compose/binaries/main/app/Playa
root=build/deb/playa
rm -rf build/deb && mkdir -p "$root/DEBIAN" "$root/opt" "$root/usr/bin" "$root/usr/share/applications"

cp -a "$app" "$root/opt/playa"
ln -s /opt/playa/bin/Playa "$root/usr/bin/playa"
for px in 16 24 32 48 64 128 256 512; do
    install -Dm644 "packaging/icons/playa-$px.png" "$root/usr/share/icons/hicolor/${px}x${px}/apps/playa.png"
done
install -m644 packaging/playa.desktop "$root/usr/share/applications/playa.desktop"

chmod -R u+rwX,go+rX,go-w "$root"
size=$(du -sk "$root" | cut -f1)
cat > "$root/DEBIAN/control" <<CONTROL
Package: playa
Version: $version
Architecture: amd64
Maintainer: Cappyjune4 <149750782+Cappyjune4@users.noreply.github.com>
Installed-Size: $size
Depends: libmpv2 | libmpv1, libsecret-1-0, ffmpeg, zenity, xdg-user-dirs, libc6, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libfreetype6, libfontconfig1, libgl1
Recommends: gnome-keyring
Section: video
Priority: optional
Homepage: https://github.com/Cappyjune4/playa-desktop
Description: Jellyfin, live TV and your own videos
 Playa for the desktop: your Jellyfin server (with downloads for watching
 offline and an admin dashboard), IPTV playlists, and the videos on this
 computer, played with mpv.
CONTROL

dpkg-deb --root-owner-group --build -Zxz "$root" "build/playa_${version}_amd64.deb" >/dev/null
echo "build/playa_${version}_amd64.deb"
