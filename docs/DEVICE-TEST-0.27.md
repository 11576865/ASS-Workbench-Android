# ASS Workbench Android 0.27 — Physical Device Validation

This is the manual gate after automated hardening. It is intentionally a device checklist, not a feature roadmap.

## Build identity before testing

Record all four values from **Diagnostics → 构建身份** before any test:

- Version name / versionCode
- Commit (12-character short SHA)
- CI run number
- APK filename

Compare them with the SHA-bearing `build-identity.txt` shipped beside the APK. Also verify the APK SHA-256 from that manifest before installing.

A result is not attributable if these identities do not match.

## Device pass

| ID | Procedure | Pass condition |
| --- | --- | --- |
| D01 | Cold start with production Fontconfig APK | editor starts; renderer initializes or enters explicit safe mode rather than crash-looping |
| D02 | Load ordinary video + ASS | video, audio and authoritative ASS preview all work |
| D03 | Load Raw ASS with extreme rotation/shear | canonical text stays unchanged; native preview suspends; correcting the value restores preview |
| D04 | Load extreme/oversized Drawing fixture | project remains editable/saveable; unsafe native preview is blocked |
| D05 | Edit Raw Event text, then change same Event through structured control | conflict UI appears; neither side is silently overwritten |
| D06 | Drag Position/Move/Origin/Rotation/Scale/Shear continuously | drag remains transient; releasing creates one reversible canonical edit |
| D07 | Timeline: disable follow, pan away, continue playback, zoom | viewport stays where manually placed except legal zero-boundary clamp |
| D08 | Start Waveform analysis, replace media/project during decode | old analysis releases; stale waveform is not published; editor stays usable |
| D09 | Use unsupported/problematic audio if available | Waveform becomes unavailable without damaging video/subtitle/project state |
| D10 | Import font while renderer is active | no native crash; refreshed preview uses published font state after the supported refresh path |
| D11 | Missing-glyph/fallback corpus | Font diagnostics and visible renderer result are explainable; disagreement is surfaced rather than hidden |
| D12 | Open MKV with multiple ASS tracks + chapters/tags/fonts; edit one track; save as new MKV | chosen track changes; track identity/order and unrelated metadata/attachments survive |
| D13 | Select manual TTF/OTF for MKV packaging | selected font appears once in output; pre-existing attachments remain |
| D14 | Package different font with same attachment filename | original remains; new attachment gets deterministic `-asswb-<hash>` name |
| D15 | Start large MKV save, continue editing current document | output represents save-start snapshot; current workspace remains dirty if later edits exist |
| D16 | Start MKV scan/save then open another workspace | stale completion/failure callback does not mutate the new workspace |
| D17 | Rotate portrait ↔ landscape during dirty edit and Raw draft | canonical document, focused Event, selection and conflict/draft semantics survive |
| D18 | Kill process after unsaved edit, relaunch and restore | expected ASS snapshot returns; stale MKV container/font write-back context does not |
| D19 | Long `\\move` / heavy transform fixture during playback and scrub | no renderer recreation loop; interaction remains recoverable |
| D20 | Long Event list during playback | unrelated workbench surfaces do not visibly churn; collect profiler/recomposition evidence if regression is suspected |

## Save/reopen closure

For every save-oriented test, reopen the generated ASS/MKV rather than accepting a success toast as evidence. For MKV tests, inspect at least:

- selected ASS track content;
- TrackNumber / TrackUID / order;
- chapters and tags;
- attachment names and counts;
- newly packaged font payload;
- video/audio playback.

## Failure recording

A failing case should record:

- test ID;
- exact build identity;
- device model and Android version;
- input fixture identity/hash when practical;
- whether failure is deterministic;
- whether canonical ASS or source MKV was altered;
- screenshot/log excerpt if available.

P0 failures are: crash/process death, data loss, silent semantic rewrite, wrong-workspace mutation, corrupted output, or renderer crash-loop. Do not bump to 0.27 while any reproducible P0 failure remains unexplained.
