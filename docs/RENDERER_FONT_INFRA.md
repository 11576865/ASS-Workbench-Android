# Renderer font infrastructure

## Current production path

ASS Workbench currently consumes `io.github.yuroyami:libmpvkt-compose:0.3.0`.

That release deliberately builds libass without a required system font provider:

```
--enable-libunibreak --disable-require-system-font-provider
```

The app therefore supplies project fonts through mpv's config directory and `subfont.ttf`. This is a valid lightweight configuration, but it has two important consequences:

1. Android system fonts are not available to libass as a general fallback catalog.
2. font-name matching for project fonts is much less forgiving than in a Fontconfig-backed build.

## Font identity model

OpenType fonts can expose several distinct names:

- name ID 1: legacy Font Family
- name ID 4: Full Name
- name ID 6: PostScript Name
- name ID 16: Typographic Family

ASS Workbench now keeps these concepts separate.

`FontMetadata.family` is human-facing and may prefer name ID 16.

`FontMetadata.rendererFamily` is the name written into ASS for the current direct-font libass path and prefers name ID 1, then Full Name, then PostScript Name.

This distinction exists because libass's direct FreeType metadata path builds its basic family/full-name index from legacy family/full-name data rather than treating typographic family as the primary family key.

## Target infrastructure

The preferred Android renderer infrastructure is:

```
ASS
  -> mpv
    -> libass
      -> Fontconfig
        -> project/imported fonts
        -> MKV embedded fonts
        -> Android system fonts
```

The first native probe keeps the libmpvKt Kotlin/JNI API unchanged and only modifies its native dependency graph:

```
libxml2 2.15.4
    |
Fontconfig 2.18.3
    |
libass 0.17.5 --enable-fontconfig
    |
mpv 0.41.0
    |
libmpvKt JNI
```

The patch is reproduced from a clean pinned `yuroyami/libmpvKt v0.3.0` checkout by:

```
tools/libmpvkt-fontconfig/patch_upstream.sh
```

No upstream native source tree is vendored into this repository.

## Migration gates

Do not switch the production APK to the custom native bundle until all of these are true:

1. arm64 native probe builds successfully;
2. the resulting libmpv/JNI pair loads on the target Android device;
3. a generated `fonts.conf` includes Android system font directories and the ASS Workbench project-font directory;
4. libass logs show expected font selection for:
   - imported CJK font,
   - MKV embedded font,
   - Android system fallback;
5. existing video playback and ASS rendering regressions are absent;
6. the custom bundle has a reproducible build manifest and pinned dependency versions.

After arm64 device verification, expand the native matrix only as needed. The current MKV write-back bridge is already arm64-only, so four-ABI renderer work is not required before the primary-device validation.

## Compatibility mode

The explicit "force global font binding" action remains useful as a deterministic compatibility tool. It rewrites Style `Fontname` values and non-empty `\fn` overrides inside ASS override blocks to one renderer family.

It is not intended to replace Fontconfig. A healthy renderer should preserve legitimate multi-font ASS files and resolve each requested face correctly.
