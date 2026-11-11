# Play Console declarations (App content)

Answers for Play Console → Policy → App content. They match what the code does.

## Data safety

- Does your app collect or share any of the required user data types? **No.**
  Orbit has no INTERNET permission; everything it reads is processed on the device and never leaves it. (Play's definition of "collect" is data transmitted off the device.)
- Is all of the user data encrypted in transit? **Not applicable** (nothing is transmitted).
- Data deletion: **not applicable.** Settings live only in app storage and are removed on uninstall.
- Privacy policy: link `docs/PRIVACY.md` (see LISTING.md).

## App access

Select "All or some functionality is restricted" and give reviewers this:

> Orbit needs two special accesses switched on before the island appears. Open Orbit and use the Setup card on the home screen:
> 1. "Notification access" → Grant → agree to the disclosure → switch Orbit on.
> 2. "Accessibility service" → Grant → agree to the disclosure → Installed apps → Orbit → switch on.
> Then play any music, start a timer, or toggle Do Not Disturb: the island appears at the top of the screen around the front camera. Tap it to expand it. No account or login is needed.

## Accessibility API declaration

- Is the app an accessibility tool (primary purpose to help people with disabilities)? **No.** (Play treats launchers, automation tools and similar as non-accessibility apps.)
- Core functionality that needs the API:
  > Orbit draws its island as an accessibility overlay window so it can sit over the status bar, camera cut-out and lock screen, which normal overlay windows cannot do. It also runs user-chosen gestures (home, back, recents, screenshot, lock screen, open the notification shade) and uses the window list (type, size, owning app) to hide the island while an app is full screen, a user-chosen app is in front, or the notification shade is open. It never reads the content of other apps' windows, does not record the screen, and sends nothing off the device (the app has no internet permission).
- Why no narrower API: no other API can draw over the status bar / lock screen, perform global actions, or report which windows are on screen.
- Prominent-disclosure video: record a short screen capture showing: launch Orbit → Setup card → tap Grant on "Accessibility service" → the disclosure dialog → "Not now" (nothing happens) → tap Grant again → "Agree and continue" → Android's accessibility settings. Upload to YouTube as unlisted and paste the link.
- Mention the API use in the store description (done in LISTING.md).

## Permissions to be ready to justify

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Fallback overlay if the accessibility service is off. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (Notification access) | Reads notifications and media sessions to show on the island. |
| `BIND_ACCESSIBILITY_SERVICE` | See above. |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Android only reveals the Wi-Fi network name to apps holding location. Used for the "Wi-Fi connected" alert only; optional, location is never read. |
| `BLUETOOTH_CONNECT` | Show which Bluetooth device connected and its battery. Optional. |
| `ANSWER_PHONE_CALLS` | Answer / hang up from the island when a call notification has no button. Optional. |
| `EXPAND_STATUS_BAR`, `MODIFY_AUDIO_SETTINGS` | Open the shade without accessibility; mute and audio-output buttons on the call card. |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Detect Wi-Fi / VPN / mobile data changes for alerts. |
| `DETECT_SCREEN_RECORDING` | Show the red recording dot (Android 15+). |
| `RECEIVE_BOOT_COMPLETED` | Reconnect the notification listener after a reboot or update. |

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was removed on purpose (Play restricts it). The Setup card opens Android's battery list instead.
