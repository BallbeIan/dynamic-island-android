# Contributing

Thanks for your interest in Island! Bug reports, device reports and pull requests are welcome.

## Device reports are the most useful thing right now

Island has been developed and tested on one Samsung foldable. How it behaves on other phones (Pixel, Xiaomi, OnePlus, Motorola, ...) is the biggest unknown. If you build it and try it, please open a **Bug report** issue even when everything works, and include:

- phone model and Android version
- whether the island lines up with your camera (and the screen, if it is a foldable)
- whether it hides while the notification panel is open and while a video is full screen
- whether timers, calls and music show up

## Building

See [Building from source](README.md#building-from-source). Run `./gradlew lint assembleDebug` before opening a pull request.

## Pull requests

- Keep changes focused, and describe what you tested and on which device.
- Match the existing style: small classes, no third-party dependencies, callbacks rather than polling.
- New settings need an on/off toggle (where it makes sense) in the settings screens.

## Licensing of contributions

The project is under the [PolyForm Noncommercial License 1.0.0](LICENSE), and the maintainer plans a paid release on Google Play. **By submitting a contribution you confirm that you wrote it (or have the right to submit it) and you grant the maintainer the right to use, modify and distribute it as part of Island, including in commercial distributions of the app.** If you are not comfortable with that, please open an issue to discuss your idea instead of a pull request.

## Reporting security problems

Please do not open a public issue for a security problem. Use GitHub's private vulnerability reporting for this repository if it is enabled, or contact the maintainer through their GitHub profile.
