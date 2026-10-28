# 😴 DozyPal

**A sleepy little pal who gets droopier with every reel you scroll.**

DozyPal is a free, just-for-fun Android app that counts how many **Facebook Reels**, **Instagram Reels**, **TikToks** and **YouTube Shorts** you watch each day. A small counter sits in the corner of the screen while you watch, and your DozyPal character (boy or girl) gets sleepier as the count goes up: heavy eyelids, dark circles, a tilted head and finally a big yawn.

No ads, no paid version, no account needed.

## Features

- 🔢 **Reel counter:** counts only reels (not long videos or the normal feed) in Facebook, Instagram, TikTok and YouTube, once you have watched one for 3 seconds.
- 🫧 **Floating counter:** a small tab docked to the left or right screen edge (drag it up or down) with adjustable opacity. It hides on the home and lock screen and ignores touches, so it never gets in the way.
- 🔘 **On/off switch:** turn counting off whenever you want.
- 👋 **Personal:** tell DozyPal your name and pick a male or female avatar. Little bubbles pop up at milestones and late at night ("Hey Nuhan, put the phone down for a bit 😴").
- 😪 **Sleepy character:** The more reels you watch, the droopier they get. Resets every day.
- 📊 **Reports:** daily, weekly and monthly charts, busiest and calmest days, and a breakdown by app.
- 🚫 **Ads aren't counted:** sponsored reels are skipped.
- 🔒 **Private:** no internet permission, nothing leaves your phone.
- 👫 **Coming soon:** friends with friend codes, leaderboards and "Scroll Battles".

## Install (Android 8.0+)

1. Open the [**latest release**](https://github.com/nuhanhabib/DozyPal/releases/latest) on your phone and download `DozyPal-vX.Y.Z.apk`.
2. Open the file. If Android asks, allow your browser or file manager to **install unknown apps**.
3. Open DozyPal and pick your character.
4. Tap **Open Accessibility settings**, then find **DozyPal** (often under *Downloaded apps*) and switch it on.
5. **Android 13 or newer:** if the switch is greyed out, go to *Settings → Apps → DozyPal*, tap **⋮** in the top corner, choose **Allow restricted settings**, and try step 4 again.

Then open reels, TikTok or YouTube Shorts and watch the counter go up.

> Android may show warnings because DozyPal isn't from the Play Store and uses Accessibility. That's expected. The full source code is here for anyone to check.

## How it works

DozyPal uses Android's **Accessibility Service**, limited to the Facebook, Instagram, TikTok and YouTube apps. When you swipe, it checks whether you're on a full-screen reels pager and moved forward to a new reel, and whether that reel is marked "Sponsored". All the rules live in [`ReelDetector.kt`](app/src/main/java/io/github/nuhanhabib/dozypal/ReelDetector.kt).

Facebook and Instagram change their apps often, so counting can sometimes miss or double-count until the detector is updated. Please [open an issue](https://github.com/nuhanhabib/DozyPal/issues) if you notice that.

## Privacy

- Only Facebook, Instagram, TikTok and YouTube are watched; events from other apps never reach DozyPal.
- It reads only labels such as "Reels" and "Sponsored" to decide what to count. It never reads messages and never saves or sends screen content.
- There is no internet permission at all. Counts are stored privately on your phone and reset daily.

See [PRIVACY.md](PRIVACY.md) for details.

## Building

You don't need Android Studio. Every push to `main` is built by GitHub Actions ([`build.yml`](.github/workflows/build.yml)), and the APK can be downloaded from the run's artifacts. Pushing a tag such as `v0.1.0` publishes a signed APK as a GitHub Release.

To build locally, you need JDK 17, the Android SDK and Gradle 8.9:

```bash
gradle :app:assembleRelease
```

Without the signing secrets this produces an unsigned APK, which is fine for testing.

## License

[MIT](LICENSE). DozyPal is not affiliated with Meta, Facebook or Instagram.
