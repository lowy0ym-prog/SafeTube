# BUILD_LOG

## Attempt 1 — baseline import (2026-11-11)
- Run: https://github.com/lowy0ym-prog/SafeTube/actions/runs/43085295282
- Result: SUCCESS (`assembleOssDebug`, JDK 17, Gradle 8.14.6, AGP 8.13.2)
- Errors: none
- Changes: imported SkyTube OSS source (upstream commit bf55983, GPLv3); removed proprietary `extra` flavor and `YouTubeAndroidPlayerApi.jar`; applicationId set to `com.lowy.safetube`.
- APK: artifact `safetube-debug-apk` on the run page above.
- Known limitations: this is UNMODIFIED SkyTube behavior. No whitelist, PIN, or fail-closed filtering yet. targetSdk is still 28. Nothing tested on a device.

## Attempt 2 — whitelist + PIN admin (2026-11-11)
- Run: https://github.com/lowy0ym-prog/SafeTube/actions/runs/43139505120
- Result: SUCCESS (compile only; this workflow version did not run unit tests)
- Changes: whitelist core, fail-closed filtering in VideoPager/PlaylistPager/NewPipeService/adapters/SubscriptionsDb, trending tabs and comments disabled, user subscribe disabled, PIN admin screen.

## Attempt 3 — CI now runs unit tests (2026-11-11)
- Run: https://github.com/lowy0ym-prog/SafeTube/actions/runs/43139763475
- Result: SUCCESS (`testOssDebugUnitTest` + `assembleOssDebug`); APK artifact `safetube-debug-apk`, test report artifact `test-reports`.
- Known limitations: targetSdk still 28; nothing run on a device/emulator; bookmarks/downloads tabs and search paging not verified on-device; see final summary.

## Attempt 4-6 — cleanup, block actions, stable signing (2026-11-12)
- Failed run 43174718975: compile errors (`youTubeVideo` out of scope in GridViewHolder; `VideoId` vs `String`). Fixed by passing the video in and using `getId()`.
- Run 43174925340: SUCCESS, release build-8.
- Changes: removed upstream "developer verification" warning dialog (FreeDroidWarn), removed LeakCanary (it adds a separate "Leaks" launcher in debug builds), disabled upstream update check, renamed app to SafeTube, removed SkyTube's own blocker/backup/security settings, added "Block this video" (video grid + player) and "Block this playlist" (no PIN to block; PIN only in the admin screen to unblock), fixed signing key (app/safetube.keystore, private repo) so updates install over earlier builds.
- Known limitations: targetSdk still 28; no on-device testing; first install of this signing key requires uninstalling any earlier SafeTube build.

## Attempt 7 — cleanup on main (2026-11-13)
- Run 43426379211 (main): SUCCESS -> release main-10. New icon, README/upstream files/links removed, SkyTube blocker toolbar button removed, mobile-data and startup "skip ads/dislike" dialogs removed, unneeded settings hidden.

## Attempts 8-10 — custom branch (2026-11-15)
- Runs 43683241139 and 43683319841 FAILED at compile: this NewPipe fork returns the description as a String (no getContent()), then getDescription() throws a checked ParsingException. Fixed both.
- Run 43683395498 (custom): SUCCESS incl. unit tests (ScreeningTest + WhitelistTest) -> release custom-13.
- Changes: 4 default channels (Professor Messer, freeCodeCamp, Bro Code, Corey Schafer) seeded on first run; men-only text screening; freeCodeCamp is "strict" (needs a credited male instructor, interviews/podcasts/playlists hidden); background worker screens every video and new uploads; admin screen has strict toggle and "always allow".
- Known limitations: screening reads text only; not tested on a device; "IT course" channel not identified, so not seeded.

## Custom branch: relaxed screening (attempt 11)
- User feedback: strict mode hid almost all of freeCodeCamp. Changed: strict is now OFF for every channel by default (opt-in per channel in the admin screen). Existing installs are migrated once (`seeded_v2`): old strict flags and old verdicts are cleared.
- Default rule now blocks only on clear text evidence: female word/name in the title, or a female first name credited as instructor/guest/host in the description. Interviews/podcasts are no longer hidden merely for their format.
- Thumbnail sample (TinyFish browser agent, 40 newest freeCodeCamp uploads): 0 thumbnails judged as clearly showing a woman (about 12 men, about 24 no person, 2 unclear). This is a sample of the newest uploads only, judged by an AI agent, not verified by a person; it does not cover the full channel.
- Untested: on a physical device. Screening is text-based and cannot see or hear videos.
- Manual check of the agent's screenshot: video JGly6PSK5iw ("Gemini 4 Argon API Crash Course") shows a woman in the thumbnail (agent had said "unclear"). Seeded as blocked once (`seeded_blocks_thumbs1`). Agent thumbnail labels are not reliable; only videos seen by eye are seeded.
