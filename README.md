# VS Launcher

A bare native Android launcher framework written in Java with platform APIs only.

## Included behavior

- Declares itself as a Home app, so Android can offer it as the default launcher.
- Home screen displays a maximum of eight installed apps, current date/time, real battery state, and an honest weather placeholder.
- Swipe left opens all apps and immediately focuses the bottom search input.
- Swipe right opens settings.
- Swipe up is configurable in Settings to open All apps or Settings.
- Tapping an app launches its exported launcher activity.

## Setup

Open the folder in Android Studio, let it install the Android Gradle Plugin, then run on a device or emulator. Press Home and select VS Launcher when Android asks which launcher to use.

Weather is deliberately shown as `not configured` until a location and weather-provider integration is selected. This avoids presenting invented weather data.

## Design decisions

- The dark graphite base reduces glare on an always-visible launcher screen. Amber appears only for the active page label and focus state.
- A vertical text list prioritizes app names and keeps eight primary choices scannable, rather than inventing icon assets.
- The persistent directional gestures are both navigation and the launcher’s identity motif. No decorative animation is used.
