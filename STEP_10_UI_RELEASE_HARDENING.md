# Physical UI Release Hardening

## Status

This document records the Step-10 presentation-layer rebuild requested after physical review rejected the oversized purple development-style interface. It does not declare physical UI PASS. Physical phone verification remains mandatory before PR #23 can be merged or the application can be called release-ready.

## Approved direction and clean-room boundary

MAX Video Player now follows the approved reference interaction model for density, hierarchy, spacing, media browsing and playback controls while retaining original MAX branding, Compose implementation, strings, icons/assets and application architecture. Reference screenshots are design references only and must not be packaged in the APK/AAB. No proprietary source, decompiled resources, logos, trademarks, exact proprietary icons or protected artwork are used.

Decoder terminology remains MAX-specific: `Auto`, `Hardware`, `Enhanced Hardware`, and `Software`. Compact player labels may use `Auto`, `HW`, `EHW`, and `SW`; `HW+` is forbidden.

## Rejected UI defects

The physical review identified release defects in the previous presentation:

- P1: library content and primary controls could collide with the status-bar/cutout region.
- P1/P2 pending physical reproducibility: the permanent top navigation strip could overflow horizontally and make destinations inaccessible.
- P2: oversized purple pills, a permanently large search field, permanently visible sort/filter chrome and development-like player surfaces produced poor phone hierarchy and reduced usable video area.

The hardening pass is therefore a presentation rebuild, not a status-bar-padding-only patch.

## System insets and edge-to-edge

Release library content is hosted inside safe drawing insets rather than universal hard-coded status-bar padding. Player video may remain immersive/edge-to-edge, while interactive player chrome and side panels respect safe drawing regions. Gesture-navigation, three-button navigation, display cutouts and landscape safe regions remain part of physical certification.

## Library redesign

The release library uses a compact title/action bar, horizontally scrollable source/category rail, lazy folder/media collections, compact progressive search, overflow sort/filter/navigation actions, thumbnail-based media rows, duration badges and a bottom-right Play action with list bottom clearance. Folder and media collections retain stable keys and the existing bounded thumbnail repository; scanning and thumbnail decoding are not moved onto the main thread.

Primary sources remain accessible without a permanently oversized navigation strip. Continue Watching, Recent, Favourites, History and settings-related actions use progressive disclosure instead of consuming permanent screen rows.

## Player redesign

The player preserves the single service-owned playback authority and existing gesture, queue, resume, decoder, audio, subtitle, Cast, private-media and orientation behavior. Presentation uses translucent player chrome, compact circular controls, a thin seek timeline with a larger interactive target, centered transport controls and horizontally scrollable tool rails so narrow landscape layouts do not clip actions.

Audio uses a right-side translucent release panel while retaining the existing production external-audio, synchronization and DSP paths. Subtitle uses the same right-side panel model while retaining embedded/external subtitle, synchronization, encoding, style and discovery behavior. Decoder uses a centered dark release modal with truthful requested/effective state. More/Tools uses a right-side scrollable release panel exposing only implemented actions and retained settings.

## Panel and Back behavior

Player menu ownership remains centralized through the existing coordinator state rather than creating competing playback engines or parallel feature implementations. Opening a player menu routes into the existing production action paths. Back/dismiss behavior closes the active overlay or panel before leaving playback according to the retained player hierarchy.

## Cast, private media and source truthfulness

The UI hardening does not re-enable local-only video processing while Cast owns playback. Private-media restrictions continue to block prohibited Cast/PiP/external-display/capture behavior and protect private metadata. Network, cloud and local media continue to use the same player architecture rather than protocol-specific player copies.

## Accessibility and responsive behavior

Interactive player controls retain at least approximately 48 dp touch targets, semantic descriptions and disabled-state truthfulness. Titles use bounded line counts/ellipsis rather than forcing horizontal overflow. Scrollable rails and panels are used where fixed-width packing would clip content. Large-font, increased-display-scale, narrow-phone, tablet, landscape, TalkBack and D-pad behavior remain required certification targets.

## Automated regression coverage

Step-10 retains the complete existing JVM and instrumentation matrix and adds release-UI expectations without weakening old production assertions. `MainActivityTest` now exercises the compact library chrome, source rail, progressively disclosed search, overflow navigation, network URL entry, playlist surface, Back behavior and activity recreation. `PlayerControlsInstrumentedTest` covers the release player primary actions, Subtitle/Decoder/More entry points, tool rail, visibility state, buffering/HUD and lock/unlock behavior.

The strict Step-10 instrumentation script explicitly executes both release-UI classes in addition to retained decoder/coexistence, Cast, TV, USB, Private Vault, settings/accessibility and sleep-timer critical classes. Each strict class must report at least one executed test; missing, skipped, zero-test or failed critical runs remain release-blocking.

## Required certification after UI source/test changes

Any UI or required-test change invalidates earlier Step-10 release-candidate evidence. The exact final PR-head SHA must independently pass Android CI, Step 8 Certification, Step 9 Certification and Step 10 Certification, including API-35 retained instrumentation and API-26/API-28 regressions where configured. A fresh APK/AAB and hashes must come from that exact certified head.

Software CI cannot substitute for the required physical UI test. On the exact certified APK, physically repeat launch → browse folder → open video → play → seek → Audio → Subtitle → Decoder → More → aspect/speed/sleep/rotation/background → return, plus rapid portrait/library scrolling and repeated landscape panel open/close/lock/unlock cycles. Capture sanitized screenshots of the main folder screen, video list, player controls, extended tool rail, Audio panel, Subtitle panel, Decoder dialog and More panel.

## Release gate

Keep PR #23 in draft and unmerged until the new exact-head software matrix is green and independent physical testing confirms there is no remaining status-bar collision, horizontal clipping, inaccessible control, broken Back behavior, player recreation, lost seek/track state or material mismatch with the approved professional interaction model.

Current physical status: **NOT VERIFIED — PHYSICAL HARDWARE UNAVAILABLE IN THIS EXECUTION ENVIRONMENT.**
