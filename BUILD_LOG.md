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
- Name check requested by user (names from his own Gemini-in-YouTube query): Jessica, Emma, Rachel, Andrea were already in the female-name list; "Ania" was missing and was added, with a test. Gemini's identification of the presenter is the user's report, not verified by me. ChatGPT's preliminary flags (ajfQWhGQGLE, lR931Iz8Z6U, soTfFFIQQ8U) are NOT seeded: it admitted it only sampled and its labels conflict with the thumbnails.
- Player: added a full-screen / exit-full-screen button to the video controls (forces landscape or portrait, works with the phone's rotation lock on). Untested on a physical device.
- User screenshot: "How an Apple Engineer Pranked Billion-Dollar Lawyers" shows a woman presenting, credit "@aniakubow". The id (PVAstrvWPkQ) comes from the ChatGPT list, which matched the title; seeded as blocked (`seeded_blocks_seen2`). Rules now also read "created by @handle" credits that start with a female given name (4+ letters).
- User request: block everything by the women he named (Ania Kubow, Jessica Chan, Emma Bostian, Rachel Fernandez, Andrea Griffiths). Added a named-people rule (name or handle anywhere in title/description, spaces and punctuation ignored) and the background scan now covers the newest ~25 pages of every approved channel (whole channel for strict ones). Limit: videos whose text never names them (e.g. a guest only shown on screen) are not caught.

## Final custom polish (attempt 12)
- Blocked more: channels UCiWmRIZ-sEbPaZZfelakuXQ (Patt Vira) and UCng0h8WiHLmT57JJ8At4LfQ (Coding with Estefania), by id/name/handle anywhere in video title/description.
- Lists: unknown videos are now screened (title + description) in parallel BEFORE a list page is shown (12 s cap; on failure the video stays unknown and the background worker retries).
- Player: a video is invisible until it is verified and starts; a blocked video closes silently (no dialog, no thumbnail).
- Branding: new notification icon, default channel picture and banner; removed all translated string folders (they contained the old app name); user-visible strings, export/backup file names, user agent and notification channel renamed to SafeTube; old website/issue/update URLs removed.
- Left on purpose: Java package name `free.rm.skytube`, GPL license headers and LICENSE/attribution (required by the license, not visible in the app).
- Untested on a physical device: real channel pictures in the left drawer (fall back to the SafeTube picture if a channel has no picture URL).

## Attempt 13: thumbnails by eye + emulator test
- Contact sheets of the newest 240 freeCodeCamp covers were generated on a CI runner and reviewed by eye: 26 videos with a woman clearly on the cover (or credited in the title) are seeded as blocked (`seeded_blocks_covers1`). First 24 covers each of Corey Schafer, Bro Code and Professor Messer show no people. Not reviewed: older freeCodeCamp videos (beyond the newest 240), and 3 uncertain covers left unblocked.
- Hid the channel-page subscribe/unsubscribe button; plain banner placeholder; removed the "invisible until playing" player change (suspected cause of blank video pages).
- CI now has an emulator job (screenshots + logs to branch `shots`). The release step only runs when the commit message contains [release].
- Emulator run findings: (1) the video player opened the old SkyTube first-run tutorial ("Swipe to view comments, 1 of 6"), which covered the player: removed. (2) the drawer listed every channel twice: de-duplicated. (3) my background screening hit YouTube's watch endpoint and caused "Sign in to confirm you're not a bot" errors: removed (non-strict channels make no extra requests). (4) Emulator IP is blocked by YouTube for watch pages, so actual video playback and description screening could not be tested there.

## Attempt 14 (custom) - emulator-verified blocking, final build
- Channel page: real banner image no longer loaded (it had the channel name drawn into it, doubling the title). Verified in emulator screenshot (Professor Messer page).
- New FeedScreen: one cached RSS request per approved channel (newest 15 videos, full descriptions). Titles/descriptions go through Screening; BLOCK verdicts recorded before the list is shown. Emulator log: all four channels read, 15 entries each; freeCodeCamp 1 blocked by default rules.
- Verification in emulator (CI, API 30): adding a test name ("beau", credited in newest freeCodeCamp video descriptions) via debug hook files/extra_blocked_people.txt made the matching video ("PostgreSQL Hybrid Search Course") disappear from the channel list; list backfilled with another video (43 -> 44 distinct texts). Blocked-title keyword check: 0 hits for 9 known-blocked titles; positive controls present.
- Whole-app walk in emulator: Preferences (Video Player, SponsorBlock, Privacy, Network and Downloads, Others, About), Watched Video Cleaner dialog, Administrator (PIN creation works, channel list, men-only screening text; screen is screenshot-blocked), no crashes in logcat, no SkyTube wording seen.
- NOT verified: actual video playback (YouTube returns 403 to the CI IP); older videos beyond the newest 15 per channel are blocked only by seeded ids, title rules and names (no description access without risking bot blocks).
- Cosmetic, left as is: Preferences screens use the light theme while the main screen is dark.

## Attempt 15 (custom) - deep check for freeCodeCamp, cleanup
- freeCodeCamp is a "deep" channel: a video stays hidden until its description is read and passes the rules. The other three channels keep only the basic rules (seeded blocked list, named people / female names in titles). Unit tests added (WhitelistTest) for hide-until-checked, queueing and progress persistence.
- Newest 15 videos: read from the RSS feed at app start and when a channel list opens (ALLOW or BLOCK recorded).
- Older videos: ScreenWorker checks one video at a time (8-10 s pause). Videos about to be shown go first (any network); the background fill of older videos runs only on an unmetered connection, can be switched off in Administrator, and stops for 30 minutes on a bot check / 403 / 429. Results and the list of found video ids are persisted, so progress resumes after a restart.
- Admin screen shows "checked X of Y" and a status line. Strict setting removed from the UI. Comments panel removed. Drawer channel pictures are now drawn in-app (letter in a coloured circle), no download.
- Emulator run (API 30): drawer shows letter avatars; admin shows "freeCodeCamp: checked 41 of 865 found so far" and "Paused: YouTube asked to slow down" (expected: the CI IP is bot-blocked, so the slow check itself cannot succeed there); name rule test still passes (Hybrid Search video removed with test name); no crashes.
- NOT verified: the slow check succeeding against YouTube (blocked from the CI IP), playback, and the Feed tab with real results beyond the first 15. Newly cleared videos appear after pull-to-refresh / reopening the list, not live.

## Attempt 16 (custom) - full freeCodeCamp history checked before the build
- GitHub (branch `deepscan`, workflow deepscan.yml) listed all 1,740 freeCodeCamp videos (yt-dlp, list pages only) and read title + full description of 1,739 of them from a public Invidious API, one at a time with 2-3.5 s pauses (about 3.5 hours, no phone, no Claude session needed). One video kept failing and stays "unchecked" (hidden until the phone checks it).
- Screening.classify (default rules, same code as in the app) was run on all 1,739: 1,720 allowed, 19 blocked (named women, "She/Mom" titles, credits). Result is bundled as assets/fcc_verdicts.txt and applied once on first start (tag fccscan1). Manual blocked list (28 videos from thumbnails) still applies on top.
- Limits: text only. A video with a woman whose name / credit is not in the title or description is not caught for older videos, except the 26 found on cover review of the newest 240.
- The phone now only has to check videos newer than this scan (RSS newest-15 on open, slow Wi-Fi check for the rest).
