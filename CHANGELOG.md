# Changelog

One section per release, newest first; each `## X.Y.Z` section becomes that tag's GitHub Release notes. Format:
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions: [SemVer](https://semver.org/).

## 0.1.0 — unreleased

Needs tv-agent's server with paired phones (its `clients.py`, after server 0.2.0) and its panel to pair.

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
