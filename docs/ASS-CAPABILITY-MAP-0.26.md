# ASS Capability Map for 0.26

This document maps ASS semantics to workbench domains. It is an information architecture map, not a command-per-button UI specification.

## Typography and appearance

| ASS syntax | Meaning | 0.26 UI treatment |
| --- | --- | --- |
| `\\fn` | font family | Typography selector; Font Manager handles resources |
| `\\fs` | font size | continuous controller + exact value |
| `\\b` | bold | discrete control |
| `\\i` | italic | discrete control |
| `\\u` | underline | discrete control |
| `\\s` | strikeout | discrete control |
| `\\fsp` | spacing | continuous controller + exact value |
| `\\fe` | encoding | advanced text option; preserve even when unsupported |
| `\\c / \\1c..\\4c` | colors | color controls with exact ASS color view |
| `\\alpha / \\1a..\\4a` | alpha | continuous + exact hex |
| `\\bord / \\xbord / \\ybord` | border | continuous controller; expose axis variants contextually |
| `\\shad / \\xshad / \\yshad` | shadow | continuous controller; expose axis variants contextually |
| `\\blur` | Gaussian blur | continuous controller |
| `\\be` | edge blur | advanced discrete/continuous control |
| `\\r` | style reset | explicit reset/inherit action |

## Geometry

| ASS syntax | Meaning | 0.26 UI treatment |
| --- | --- | --- |
| `\\an` / legacy `\\a` | alignment | 9-grid |
| margins | Style/Event margins | Position tool |
| `\\pos` | fixed position | direct drag + exact X/Y |
| `\\move` | motion path | start/end handles + path + exact timing |
| `\\org` | transform origin | draggable origin handle |
| `\\fr / \\frz` | Z rotation | rotation handle + exact degrees |
| `\\frx / \\fry` | X/Y rotation | advanced geometry controllers |
| `\\fscx / \\fscy` | scale | bounding-box handles + exact percent |
| `\\fax / \\fay` | shear | advanced geometry controller |
| `\\clip / \\iclip` rectangle | clipping | draggable rectangle |
| vector clip | vector clipping | path editor, later phase |

## Animation and temporal effects

| ASS syntax | Meaning | 0.26 UI treatment |
| --- | --- | --- |
| `\\fad` | simple fade | In/Out sliders + exact ms |
| `\\fade` | advanced fade | structured advanced editor |
| `\\t` | transform | transform segment editor |
| transform accel | easing exponent | exact + continuous controller |
| multiple `\\t` | layered transforms | ordered transform list |

Structured animation edits must preserve unsupported/unknown transform tags.

## Karaoke

| ASS syntax | Meaning | Planned treatment |
| --- | --- | --- |
| `\\k` | basic karaoke | syllable timing lane |
| `\\K / \\kf` | progressive fill | syllable timing lane |
| `\\ko` | outline karaoke | syllable timing lane |
| `\\kt` | timing control | advanced timing |

Karaoke is a sub-workspace rather than a set of permanent Event chips.

## Drawing

| ASS syntax | Meaning | Planned treatment |
| --- | --- | --- |
| `\\p` | drawing mode | drawing workspace |
| `\\pbo` | drawing baseline offset | exact + visual offset |
| `m n l b s p c` | drawing path commands | lossless path model + direct edit |

## Event-level fields

- Layer
- Start
- End
- Style
- Name/Actor
- Margin L/R/V
- Effect
- Text
- Comment/Dialogue

These remain Event semantics even if a related domain tool can edit their effective visual result.

## Parser policy

The capability map does not imply that only listed tags are valid.

For every override block:

1. tokenize losslessly;
2. identify known tags when possible;
3. keep unknown tokens verbatim;
4. associate structured controls with owned known spans;
5. rewrite only owned spans;
6. preserve order and surrounding text unless a specific operation requires a local rewrite.

## Effective-value policy

The UI should be able to explain, for any supported property:

Style base
  -> Event field where applicable
  -> inline override
  -> active transform at current preview time
  -> effective value

The user should not need to guess whether a value comes from Style or override.

## Controller policy

Use a continuous controller only when the parameter genuinely has useful continuity.

Good candidates:
- size;
- spacing;
- border;
- shadow;
- blur;
- alpha;
- scale;
- rotation;
- fade duration;
- transform acceleration.

Use discrete controls for:
- alignment;
- bold/italic/underline/strike;
- Style selection;
- Comment;
- Layer;
- font family selection.

Every continuous controller intended for professional work should retain exact numeric entry.
