# Able requirements traceability matrix

This matrix connects functional requirements (FR) to use cases (UC), stories (US), and planned verification cases (T). Verification cases are plans only; they have not been executed.

| Requirement IDs | Use case | User stories | Planned verification |
| --- | --- | --- | --- |
| SHELL-001 (navigation re-select revised 2026-11-22, ADR-040) | UC-03, UC-48 | US-UI-01, US-UI-02 | T-UI-01, T-UI-02, T-TV-01 |
| HOME-001–HOME-003 | UC-01, UC-03, UC-20 | US-HOME-01 | T-HOME-01, T-UI-01 |
| SET-001 | UC-02 | US-SET-01 | T-LOCALE-01 |
| SET-002 | UC-01 | US-SET-02 | T-PERM-01, T-PERM-02 |
| PRIV-001 | UC-18 | US-PRIV-01 | T-TELE-01 |
| UX-001 | UC-01, UC-34 | US-UX-01 | T-TOUR-01, T-TV-01 |
| UX-002 | UC-35 | US-UX-02 | T-HELP-01 |
| UX-003 (revised 2026-11-22) | UC-36 | US-UX-03 | T-HAPTIC-01 |
| UX-004 (revised 2026-11-22; small-card container-transform defect criteria) | UC-36 | US-UX-04 | T-MOTION-01, T-MOTION-02, T-MOTION-03 |
| SEARCH-001 | UC-42 | US-SEARCH-01 | T-SEARCH-01, T-TV-01 |
| BRAND-001 (rebrand in progress, decision pending) | UC-37 | US-BRAND-01, US-CUST-01 | T-BRAND-01 |
| ABOUT-001 (repository `https://github.com/dragolab/able`, 2026-11-22) | UC-19 | US-ABOUT-01, US-APP-03 | T-ABOUT-01, T-REPO-01 |
| BAT-001–BAT-004 (BAT-001–BAT-003 revised 2026-11-22: custom profiles, concurrent activation, most restrictive wins; ADR-005) | UC-04, UC-05, UC-43 | US-BAT-01, US-BAT-07, US-BAT-08 | T-PROFILE-01, T-PROFILE-02, T-PROFILE-04, T-PROFILE-05 |
| BAT-005–BAT-008 (BAT-007 revised 2026-11-22: manual RAM tool allowed, automatic RAM cleaning excluded) | UC-05, UC-45 | US-BAT-02, US-BAT-08, US-MEM-01 | T-PROFILE-03, T-PROFILE-05, T-STATE-01, T-MEM-02 |
| MON-001–MON-008 | UC-06 | US-MON-01 | T-MON-01, T-MON-02, T-POWER-01 |
| MON-009 | UC-06 | US-MON-02 | T-MON-03 |
| BASE-001–BASE-002 | UC-16, UC-17 | US-BASE-01, US-BASE-02 | T-BASE-01, T-BASE-02 |
| BAT-REV-001–BAT-REV-002 | UC-22 | US-BAT-03 | T-BAT-REV-01 |
| BAT-CHG-001, BAT-NOTE-001 | UC-23 | US-BAT-04 | T-BAT-CHG-01 |
| BAT-CHK-001 | UC-24 | US-BAT-05 | T-BAT-CHK-01 |
| BAT-HLT-001–BAT-HLT-002 | UC-25 | US-BAT-06 | T-BAT-HLT-01, T-CAP-01 |
| LOCK-001–LOCK-002 | UC-07 | US-LOCK-01 | T-LOCK-01 |
| LOCK-003 (platform-dependent; Precise mode added 2026-11-22, ADR-039) | UC-39, UC-47, UC-07 | US-LOCK-02, US-LOCK-03 | T-LOCK-02, T-LOCK-03, T-LOCK-04 |
| CMD-001 | UC-38 | US-CMD-01 | T-CMD-01, T-CMD-02, T-SHIZUKU-01 |
| APPLOCK-001 | UC-40 | US-APPLOCK-01 | T-APPLOCK-01, T-APPLOCK-02 |
| KEY-001 | UC-41 | US-KEY-01 | T-KEY-01 |
| TORCH-001–TORCH-002 | UC-08 | US-TORCH-01 | T-TORCH-01, T-CAP-01 |
| HOT-001–HOT-002 (revised 2026-11-22: Shizuku control and SoftAP configuration, ADR-038) | UC-09, UC-46 | US-HOT-01, US-HOT-02 | T-HOT-01, T-HOT-02, T-HOT-03, T-SHIZUKU-01 |
| APP-001–APP-005 | UC-10 | US-APP-01 | T-APP-01, T-APP-02, T-SHIZUKU-01 |
| APP-004 (archive/restore, revised 2026-11-22; APK backup for user apps, ADR-033), APP-005 | UC-32 | US-APP-02 | T-APP-03, T-APP-05, T-SHIZUKU-01 |
| APP-006 | UC-33 | US-APP-03 | T-APP-04 |
| APP-007 (added 2026-11-22, ADR-036) | UC-44 | US-APP-04 | T-APP-06, T-APP-07, T-SHIZUKU-01 |
| MEM-001 (added 2026-11-22, ADR-037) | UC-45 | US-MEM-01 | T-MEM-01, T-MEM-02, T-SHIZUKU-01 |
| STO-001–STO-002 | UC-11 | US-STO-01 | T-STORAGE-01, T-STORAGE-02 |
| STO-002 (system-wide cache, revised 2026-11-22) | UC-31 | US-STO-03 | T-STORAGE-04, T-SHIZUKU-01 |
| STO-003 | UC-30 | US-STO-02 | T-STORAGE-03 |
| ACT-001–ACT-002 | UC-12 | US-ACT-01 | T-ACT-01 |
| SHORT-001–SHORT-003 | UC-12, UC-13 | US-ACT-01, US-SHORT-01 | T-SHORT-01, T-TV-01 |
| PIX-001–PIX-006 | UC-21 | US-PIX-01 | T-PIXEL-01, T-CAP-01 |
| GPS-001, GPS-002 | UC-26 | US-GPS-01 | T-GPS-01 |
| SENS-001, SENS-002, SENS-003 | UC-27 | US-SENS-01 | T-SENS-01, T-CAP-01 |
| SYS-001, SYS-002, SYS-003, SYS-004 (SYS-001 NFC routes and SYS-003 Wireless debugging page revised 2026-11-22) | UC-28 | US-SYS-01 | T-SYS-01, T-SYS-02, T-SYS-03, T-SHIZUKU-01 |
| CUST-001, CUST-002 | UC-29 | US-CUST-01 | T-CUST-01, T-BRAND-01 |
| CFG-001–CFG-003 | UC-14 | US-CFG-01 | T-EXPORT-01 |
| CFG-004–CFG-007 | UC-15 | US-CFG-02 | T-IMPORT-01, T-IMPORT-02, T-IMPORT-03 |

## Non-functional traceability

| NFR IDs | Related requirements | Planned verification |
| --- | --- | --- |
| NFR-PRIV-001-003, NFR-SEC-003 | PRIV-001, APP-001, APP-006, CFG-001-CFG-007, MON-009, BAT-CHG-001, BAT-NOTE-001, GPS-001–GPS-002, CMD-001 (history/output), SEARCH-001 (recent searches), APP-004 (APK backups never exported) | T-TELE-01, T-EXPORT-01, T-IMPORT-01, T-MON-03, T-BAT-CHG-01, T-GPS-01, T-CMD-02, T-SEARCH-01, T-APP-05 |
| NFR-SEC-001 | CFG-002, CFG-004 | T-IMPORT-01 |
| NFR-SEC-003 (hotspot password) | HOT-001 (SoftAP configuration, QR), CFG-003 | T-HOT-02, T-EXPORT-01 |
| NFR-SEC-002 | SET-002, LOCK-001, LOCK-003, MON-008, HOT-002, BASE-001, BASE-002, GPS-001, SYS-001–SYS-004, CMD-001, APPLOCK-001, KEY-001 | T-PERM-01, T-BASE-01, T-BASE-02, T-GPS-01, T-SYS-01, T-CMD-01, T-APPLOCK-01, T-KEY-01 |
| NFR-REL-001–002 | BAT-002, BAT-005–BAT-008, APP-003–APP-005, APP-007, MEM-001, HOT-001, STO-002, CFG-005–CFG-007, CMD-001 | T-STATE-01, T-PROFILE-05, T-APP-02, T-APP-05, T-APP-06, T-MEM-01, T-HOT-02, T-IMPORT-03, T-CMD-01 |
| NFR-PWR-001–002 | BAT-007–BAT-008, MEM-001 (honest copy), MON-001–MON-008, BAT-REV-001–002, BAT-HLT-001–002, PIX-006, SENS-001–SENS-003 | T-MON-02, T-POWER-01, T-BAT-REV-01, T-BAT-HLT-01, T-PIXEL-01, T-SENS-01, T-MEM-02 |
| NFR-UI-001, NFR-TV-001 | SHELL-001 (re-select), BAT-002 (Status screen), HOME-001, SHORT-003, ABOUT-001, BAT-CHK-001, CUST-001–CUST-002, UX-001, UX-004, SEARCH-001 | T-UI-01, T-TV-01, T-ABOUT-01, T-BAT-CHK-01, T-CUST-01, T-TOUR-01, T-MOTION-01, T-UI-02 |
| NFR-I18N-001 | ABOUT-001 | T-LOCALE-01, T-ABOUT-01 |
| NFR-ACC-001, NFR-I18N-001 | SHELL-001, HOME-001, SET-001, UX-001, UX-002, SEARCH-001 | T-LOCALE-01, T-ACCESS-01, T-HELP-01, T-SEARCH-01 |
| NFR-ACC-002 (revised 2026-11-22: card headers, human-formatted values) | SET-002, UX-001–UX-004, BRAND-001, STO-003, BAT-HLT-001–BAT-HLT-002, APP-006 | T-ACCESS-02, T-ACCESS-03, T-PERM-02, T-MOTION-01, T-BRAND-01 |
| NFR-COMPAT-001 | TORCH-001, HOT-001–HOT-002, APP-007, MEM-001, LOCK-001, LOCK-003 (Light/Precise), APPLOCK-001, KEY-001, CMD-001, PIX-001–PIX-006, BAT-HLT-001–BAT-HLT-002, SENS-001–SENS-003, SYS-001–SYS-004 | T-CAP-01, T-TORCH-01, T-HOT-01, T-PIXEL-01, T-BAT-HLT-01, T-SENS-01, T-SYS-01, T-SYS-02, T-SYS-03, T-LOCK-02, T-LOCK-03, T-LOCK-04, T-APPLOCK-02, T-KEY-01, T-HOT-02, T-APP-07, T-MEM-01 |
| NFR-MAINT-001 | All FRs; public repository hygiene (ADR-011 revised) | Traceability review for each requirement change; T-REPO-01 |
| NFR-PERF-001 | APP-001, APP-006, STO-001, STO-003, CFG-004, CMD-001 (streaming output), SEARCH-001 | T-PERF-01, T-STORAGE-03, T-APP-04, T-CMD-01, T-SEARCH-01 |

## ID notes

- The owner's 2026-11-22 request labelled the command shell tool "SHELL-001". SHELL-001 already identifies the app shell/navigation requirement, so the tool is CMD-001; SHELL-001 is unchanged.
- v0.18 (2026-11-22) adds APP-007, MEM-001, UC-43–UC-48, US-BAT-07, US-BAT-08, US-HOT-02, US-LOCK-03, US-UI-02, US-APP-04, US-MEM-01, and the verification plans listed above; BAT-001–BAT-003, BAT-007, SHELL-001, UX-004, LOCK-003, HOT-001–HOT-002, SYS-001, SYS-003, ABOUT-001, and BRAND-001 keep their IDs and are revised in place. The superseded fixed profile priority is kept as a history note in the specification and ADR-005.

## Change control

When a requirement changes, update the product specification, linked story/use case, verification plan, and this matrix in the same change. If a requirement is removed, retain its ID as deprecated with a reason; do not reuse it.
