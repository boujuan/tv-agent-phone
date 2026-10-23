# TV Agent for phones

An Android app that controls [tv-agent](https://github.com/boujuan/tv-agent), the voice agent for an Android TV,
from your phone: a remote with a d-pad, your voice (hold to talk), what's playing with its tracks and skips, search
and the "what was I watching / what's new" lists, the activity history (mark wrong, teach), and the agent's decision
pipeline and settings. It talks only to your own computer running tv-agent, over HTTPS pinned to that computer's
certificate; no Google Play services, no account, no cloud of its own.

| Screen | What it does |
|---|---|
| **Remote** | D-pad and OK (hold OK for a long press), back, home, play/pause, -10 s / +30 s, sleep and wake, info and menu, a number pad (the TV app's lists), volume keys, an exact volume slider and mute, the app shortcuts the agent may open, and a keyboard that types into the TV's focused field |
| **Voice** (every main screen) | Hold the mic button while you speak, or tap it and speak: streamed to the agent like the TV remote's mic, so it decides in your pause; shows what it heard and did. Also a Quick Settings tile and a home-screen widget |
| **Playing** | Title, episode and poster, a seek bar, pause/resume, -10 s / +30 s, previous and next episode, skip the intro / recap / credits (a highlighted button while you're in one), stop, and Kodi's audio and subtitle tracks |
| **Search** | Type a title or any command ("the bear season 3", "lofi on youtube"), quick commands, and the Watching / New episodes / New films lists with posters: tap one to play it |
| **Activity** | The agent's history (remote, phone, panel, typed, Claude), newest first with paging, each with its response times; a chart of time-to-decision; mark a command wrong, teach the right action, do it again, play its recording |
| **More › Overview** | The agent (version, models, since when) and the TV (model, screen, app in front, network, ping, CPU, memory, TV app version), with wake, sleep, reconnect, diagnose, speed test and a screenshot; latency per stage |
| **More › Decisions** | The decision steps (order, on/off), the LLM (model, reasoning, private providers), d1 (where, hosted model), speech-to-text (where, models, loaded, keep recordings), taught sentences, and "try a sentence" |
| **More › Settings** | The agent's player, highest resolution and bitrate, link check, binge mode, auto skip, audio / native / subtitle languages (ordered), Twitch favourites, history on/off, each saved live |
| **More › Agents** | Every computer this phone is paired with: switch, edit its addresses (e.g. LAN then Tailscale), forget (here and on the agent) |
| **More › This app** | Theme (system, light, dark), colours from the wallpaper, vibration on remote presses |
| **More › About** | Versions (this app, the agent, the TV app), licence and links; checks GitHub for a newer release and installs it, by itself every 12 hours if you leave *Update automatically* on |

English and Spanish (from the phone's language). Android 10 (API 29) or newer; built for Android 17 (API 37).

## What it needs

- **tv-agent** server 0.3.0 or newer (paired phones): its phones' listener runs by
  default (`phones = true`) on this computer's LAN address, HTTPS on port 8768 and voice on 8769. Add
  `phone_hosts = ["<Tailscale address>"]` to `~/.config/tv-agent/config.toml` to reach it away from home too.
- **The TV Agent panel** on that computer, to pair (Settings › Phones › Pair a phone…). `qrencode` installed gives
  it a QR code; without it, type the code.

## Set up

1. Build and install (JDK 17 and the Android SDK with Platform 37.0; `local.properties` with `sdk.dir=…`, which
   isn't committed; the Gradle wrapper fetches Gradle 9.8.2, checked against its SHA-256):

   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

   Or install a release APK from GitHub Releases (signed with the project's release key, see
   [RELEASING.md](RELEASING.md)). A release install updates itself from then on (More › About): let TV Agent
   install apps without asking (Android 17: Install apps › Always allow; before: Install unknown apps).
2. On the computer: the panel's **Settings › Phones › Pair a phone…** shows a QR code and a code (5 minutes, one use).
3. On the phone: open TV Agent, **Scan the QR code**. Or **Type the code** with the computer's address: the app
   then shows the certificate's fingerprint, which must be the one under the code in the panel.
4. On Android 17, allow access to the local network (asked at the start; without it the app reaches the computer
   only on an address outside the LAN, such as Tailscale). The first time you speak, allow the microphone; the
   first scan, the camera.

To control the TV from the Quick Settings panel, edit the tiles and add **TV command**; the home-screen widget is a
single microphone button. Both open a small panel that listens at once and closes after the result.

## How it stays private

- **Pairing** gives this phone its own token; the agent keeps only its SHA-256 and can revoke it at once (the panel's
  Settings › Phones, or here: Agents › Forget, which tells the agent too).
- **Every call is HTTPS pinned** to the agent's own certificate, by the SHA-256 the QR code carried (or you compared):
  on another network with the same addresses, nothing is sent, not even the token. Host names aren't checked
  because the pin names the one certificate, and the laptop's address may change.
- **The token is sealed** with AES-256-GCM under an Android Keystore key that can't leave the phone; the app's data
  isn't backed up (a restored copy couldn't open it anyway).
- **The phone never gets** the agent's API keys, Stremio key, Kodi password or stream links (the agent doesn't serve
  them to phones), and its settings are the playback and language ones, not addresses or ports.
- **The microphone** records only while you hold the button or a command is being heard (12 s at most), straight to
  your agent. Posters come from the addresses the agent gives (Cinemeta / metahub, over https).
- **Updates** come only from this repository's GitHub Releases (api.github.com, no account): an APK is installed only
  if its SHA-256 matches the digest GitHub records, it's this app with a higher version, and it's signed with the
  release key alone (the certificate's SHA-256 is pinned in the app). Switch *Update automatically* off in About to
  check by hand only.

## Develop

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
# Against a running agent too (pairs, uses the API and the voice stream, unpairs): a pairing link from the panel
./gradlew testDebugUnitTest --tests '*LiveAgentTest' -PtvAgentPairLink='tvagent://pair?…'
```

| Path | What it is |
|---|---|
| `app/src/main/java/…/data/Api.kt` | The typed API client (OkHttp + kotlinx.serialization): pinned TLS, the agent's addresses in turn, the actions |
| `data/Models.kt` | The agent's answers |
| `data/Pairing.kt`, `Tls.kt` | The QR code's link, typed codes and addresses; the certificate pin and the fingerprint of a typed address |
| `data/AgentStore.kt`, `AgentRepository.kt` | Paired agents in DataStore, tokens sealed with the Keystore; the agent in use |
| `data/VoiceStream.kt`, `Recorder.kt`, `VoiceController.kt` | The agent's audio stream protocol over TLS, 16 kHz mono PCM16 from the mic, one spoken command (streamed, or sent whole) |
| `ui/` | One Compose screen and ViewModel per file: `remote/`, `playing/`, `search/`, `activity/`, `overview/`, `decisions/`, `settings/`, `agents/` (pairing with CameraX + ZXing), `voice/` |
| `MainActivity.kt`, `VoiceActivity.kt` | The app; the voice panel for the Quick Settings tile and the widget |
| `app/src/test/` | Unit tests: pairing links, the client against a pinned TLS server (MockWebServer), address fallback, the stream protocol, storage |

The agent's API: tv-agent's `server/README.md` (HTTP API). Single activity, Compose and Material 3, ViewModels with
StateFlow; no dependency injection framework (`TvAgentApp` holds the few long-lived objects).

## Licence

MIT, see [LICENSE](LICENSE).
