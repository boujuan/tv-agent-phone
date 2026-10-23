# Changelog

One section per release, newest first; each `## X.Y.Z` section becomes that tag's GitHub Release notes. Format:
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions: [SemVer](https://semver.org/).

## 0.2.2 — 2026-10-24

### Added
- **The agent's addresses stay current by themselves**: whenever the agent answers (at start, on return, every
  15 s while the app is open) the app reads the addresses it listens on now (`addresses` in
  `GET /v1/clients/me`, tv-agent with it) and saves them, the one that answered first and any typed in by hand kept
  after the agent's. A new LAN address, or Tailscale switched on, needs no pairing again and nothing typed. Every
  address is still pinned to the agent's certificate; an agent without `addresses` changes nothing.

## 0.2.1 — 2026-10-24

### Added
- **More › Settings › Player** offers **TV Agent Player** (tv-agent's own player on the TV, with tv-agent from its
  `tvplayer` support on), and Playing names it and shows its tracks as it does Kodi's.
- **More › Settings**: *Check each episode is the one asked for, by its subtitles* (`check_episodes`), and the
  agent's own **Updates**: install them by themselves, check and tell me, or don't check (`updates`; this app's
  updates stay in About).
- **More › About** shows TV Agent Player's version on the TV, and says when the agent has found a newer release of
  it (`components.tvplayer` in `GET /v1/updates`), as for the TV app and the agent.

### Fixed
- About without the agent (the computer off, or at a new address): its error shows in the agent's card instead of
  over the screen, so it no longer looks as if *Check for updates* needed the agent (it asks GitHub only). A check
  that finds nothing newer now says so, and the status shows when it last checked.

## 0.2.0 — 2026-10-23

Works with any tv-agent server 0.3.0 or newer; one that reads it shows each phone's app version in the panel.

### Added
- **More › About**: this app's version and build and the key it's signed with, the agent it's paired with (name,
  server version), the TV app's version, the licence, and links to the source, releases and changelog.
- **Updates from GitHub Releases**: *Check for updates* shows the latest release against this one, with its notes;
  *Update now* downloads the APK, checks its SHA-256 against the digest GitHub records and that it's this app, newer,
  and signed with the release key only, then installs it. *Update automatically* (on by default) does the same every
  12 hours. Never an older version. Once TV Agent may install apps (Android 17: Install apps › Always allow; before:
  Install unknown apps), updates install without asking (Android 12 and newer); until then Android asks on *Update
  now*, and the automatic check only notifies. Notifications say an update was installed, is waiting or failed.
- The app sends its version with every call (`X-App-Version`) and in the voice stream's first line (`app_version`),
  so the agent can list each phone's version; About shows what the agent knows of its own updates when it has
  `GET /v1/updates`.

## 0.1.0 — 2026-10-23

Needs tv-agent server 0.3.0 or newer (paired phones, the full vocabulary list, the remote's gear and input keys)
and panel 0.3.0 to pair. Built for Android 17 (API 37; Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21), runs on Android 10
and newer.

### Added
- Pairing with an agent from the panel's QR code (CameraX + ZXing, no Google Play services) or a typed code with
  the certificate's fingerprint to compare; several agents, switchable; their addresses tried in order (LAN, then
  e.g. Tailscale).
- HTTPS pinned to the agent's own certificate; the token sealed with an Android Keystore key; no backups.
- Remote: d-pad and OK (long press too), back, home, play/pause, skips, sleep and wake, info, menu, number pad,
  volume keys, an exact volume slider and mute, app shortcuts, typing into the TV; haptics.
- Voice: hold to talk or tap and speak, streamed to the agent over TLS (decided in your pause), or sent whole when
  the stream isn't available; a Quick Settings tile and a home-screen widget.
- Playing: title, episode, poster, seek bar, play/pause, skips, previous/next episode, skip intro/recap/credits,
  stop, Kodi's audio and subtitle tracks.
- Search: a title or any command, quick commands, and the Watching / New episodes / New films lists to play from.
- Activity: the history with response times and a chart, mark wrong, teach the right action, do again, play the
  recording.
- Overview (agent and TV status, wake, sleep, reconnect, diagnose, speed test, screenshot, latency per stage),
  Decisions (steps, models, speech-to-text, taught sentences, try a sentence), Settings (player, quality, languages,
  binge, auto skip, Twitch, history), Agents, This app (theme, colours, haptics).
- English and Spanish.
- Asks for Android 17's local-network access, which reaching the computer on the LAN needs.
- The LLM, decision-model and speech-to-text models picked from the panel's benchmarked lists (or any OpenRouter id);
  the speech-to-text vocabulary edited on its own screen; the remote's TV settings (gear) and Inputs keys.
