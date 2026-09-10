# Step 10 Known Limitations

## Certification limitations

1. **Physical hardware unavailable to this execution environment.** Real phones/tablets, Android TV/Google TV, Cast receiver, Bluetooth/headset, USB-OTG/removable storage, biometric hardware, HDR display, external display and real NAS endpoints have not been physically certified here.
2. **3 GB+ physical-media certification is not complete.** Existing CI arithmetic/virtual fixtures do not substitute for the required real source larger than 3 GB.
3. **4K/HDR/4K60 physical certification is not complete.** Device capability must be recorded per device; unsupported hardware should be marked `NOT SUPPORTED BY DEVICE`.
4. **Performance/endurance/battery/thermal metrics are not available without a physical test target.** No synthetic values are presented.
5. **Production signing/publishing is not performed.** Signing credentials must never be committed; store publication requires separate explicit authorization.

## Release consequence

These limitations block full Step-10 PASS even when all software CI gates are green. Current intended status while they remain unresolved:

`STEP 10: PARTIAL — SOFTWARE CERTIFIED, PHYSICAL CERTIFICATION INCOMPLETE`

There are no knowingly accepted P0/P1 product defects in this document; any P0/P1 discovered by CI or physical testing must block release until fixed and regressed.
