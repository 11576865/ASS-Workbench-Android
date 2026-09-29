# Renderer validation matrix

This document defines the minimum validation required before the Fontconfig-enabled renderer becomes the default Android preview path.

## Startup gates

| ID | Probe | Pass condition |
| --- | --- | --- |
| S1 | Native load | libmpv and JNI load; client API version is readable |
| S2 | mpv core create | Android bridge initialization and mpv_create complete |
| S3 | Minimal options | config-dir, fonts dir, provider and logging options are accepted |
| S4 | Minimal initialize | mpv_initialize completes with provider=fontconfig |
| S5 | Full options | ASS Workbench's complete MpvOptions set is accepted |
| S6 | Full initialize | complete renderer initializes without abort/SIGSEGV |
| S7 | Compose preview | rememberMpv and preview handoff complete |
| S8 | Content protocol | content:// stream provider registers successfully |

A native crash must leave renderer-startup-probe.txt at the last "starting" stage.

## Font resolution matrix

| ID | Source | Scenario | Expected result |
| --- | --- | --- | --- |
| F1 | Imported project font | ASS Style Fontname matches legacy family | project font selected |
| F2 | Imported project font | ASS requests typographic-only alias | diagnostics show alias; renderer uses compatible family after explicit correction |
| F3 | Android system font | no project copy exists | Fontconfig selects a system face |
| F4 | MKV embedded font | matching attachment present | embedded face is selectable without manual extraction |
| F5 | Multi-font ASS | several legitimate Style/\fn families | individual families remain intact; no global rewrite required |
| F6 | Missing glyph | requested face lacks one CJK glyph | fallback font is selected and logged |
| F7 | Missing family | neither project nor system match exists | controlled fallback or explicit failure; app must not crash |
| F8 | provider=none | same ASS as F1/F5 | compatibility path still works with embedded/project fonts where exact matching allows it |

## Cache lifecycle

The Fontconfig cache identity includes:
- policy schema version;
- Android Build.FINGERPRINT;
- imported font file name, size and modification time.

Required checks:
1. importing a new font changes the cache fingerprint;
2. replacing a font changes the cache fingerprint;
3. an Android system-image change changes the environment fingerprint;
4. "重建缓存" removes old Fontconfig cache directories and recreates the renderer;
5. cache corruption never requires clearing all app data.

## Container matrix

| ID | Scenario | Expected result |
| --- | --- | --- |
| M1 | MKV without attachments | opens without renderer/native crash |
| M2 | MKV with one TTF/OTF attachment | attachment is registered and usable |
| M3 | MKV with many font attachments | scan remains streaming and bounded in memory |
| M4 | MKV with multiple ASS tracks | selected track keeps its font semantics |
| M5 | edited ASS write-back | no video/audio transcode; track identity/order retained |
| M6 | selected manual TTF/OTF packaged during ASS write-back | source attachments remain; each selected font is appended once; output reopens |
| M7 | source already contains the selected font | Android packaging plan does not add the same SHA-256 again |
| M8 | attachment filename collision with different payload | no source attachment is overwritten or silently lost; collision policy is explicit |

## Diagnostics requirements

Before the Fontconfig renderer becomes the default, the UI/log path must expose:
- requested provider;
- detected provider;
- provider setup ready/not-ready;
- ASS requested family;
- selected file/source;
- face index/PostScript name when available;
- glyph fallback attempts;
- final fallback failure;
- renderer build manifest.

## Promotion gate

Do not make Fontconfig the default renderer until all of the following are true:
1. S1-S8 pass on the affected physical Android device;
2. F1-F8 have been exercised at least once;
3. M1-M4 do not crash or show unbounded memory growth;
4. provider diagnostics show Fontconfig actually initialized;
5. cache rebuild is verified;
6. ordinary provider=none production builds remain available as a compatibility fallback;
7. no material startup-time, memory or playback regression is observed.
