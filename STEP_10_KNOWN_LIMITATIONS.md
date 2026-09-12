# Step 10 Known Limitations

## Certification limitations

1. **Physical hardware unavailable to this execution environment.** Real phones/tablets, Android TV/Google TV, Cast receiver, Bluetooth/headset, USB-OTG/removable storage, biometric hardware, HDR display, external display and real NAS endpoints have not been physically certified here.
2. **Physical UI release-hardening verification is incomplete.** The exact final UI-hardened APK still requires physical-phone verification of system insets/cutouts, gesture-navigation and 3-button-navigation clearance, portrait/landscape layout, horizontal source/tool scrolling, panel usability, Back/close behavior, large font and increased Android display size. The required sanitized screenshot set is: main folder screen, video-list screen, player controls, expanded tool rail, Audio panel, Subtitle panel, Decoder dialog and More panel.
3. **The original physical UI defects are software-corrected but not physically closed.** Status-bar/primary-control overlap was classified P1; unusable horizontal navigation overflow P1/P2 depending reproducibility; poor phone hierarchy/density P2. CI/emulator evidence cannot by itself close those physical-review defects.
4. **3 GB+ physical-media certification is not complete.** Existing CI arithmetic/virtual fixtures do not substitute for the required real source larger than 3 GB.
5. **4K/HDR/4K60 physical certification is not complete.** Device capability must be recorded per device; unsupported hardware should be marked `NOT SUPPORTED BY DEVICE`.
6. **Performance/endurance/battery/thermal metrics are not available without a physical test target.** No synthetic values are presented.
7. **Production signing/publishing is not performed.** Signing credentials must never be committed; store publication requires separate explicit authorization.

## Exact-candidate consequence

Any production UI source change invalidates previous Step-10 release-candidate evidence. Historical pre-UI workflow runs and APK/AAB hashes remain useful regression evidence, but they are not final release evidence for the redesigned interface. The exact final PR-head SHA must pass Android CI, Step 8 Certification, Step 9 Certification, Step 10 Certification and the retained UI instrumentation before its generated APK/AAB can be used for physical UI verification.

## Release consequence

These limitations block full Step-10 PASS even when all software CI gates are green. Current intended status while they remain unresolved:

`STEP 10: PARTIAL — SOFTWARE/CI STATUS IS EXACT-HEAD-DEPENDENT; PHYSICAL CERTIFICATION INCOMPLETE`

There are no knowingly accepted P0/P1 product defects in this document. The software correction for the original P1 inset/clipping defect must still be physically verified, and any P0/P1 discovered by CI or physical testing blocks release until fixed and regressed.
