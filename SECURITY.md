# Security

## Reporting a problem

Please report security issues privately via GitHub's **Report a vulnerability** button on the Security tab, rather than opening a public issue.

## Security checklist

| Check | Status in DozyPal |
|---|---|
| Hide API keys / check env variables | Signing secrets and the Firebase config (`google-services.json`) live only in GitHub Actions secrets and are written to the build machine at build time, then deleted. |
| Secrets in git history | `.gitignore` blocks keystores, `.env` files and `google-services.json`. The full history was audited (no keys, passwords or config files ever committed). GitHub secret scanning **and push protection** are on, so a secret is blocked before it lands. |
| Proper authentication | Firebase Authentication (anonymous accounts, no email or password). Every database rule requires a signed-in user. |
| Users access own data | [`firestore.rules`](firestore.rules): only you can write your profile; it is readable only by you and your friends; listing all users is blocked. |
| Protect admin routes | There are no admin endpoints; everything not explicitly allowed in the rules is denied. |
| Secure database | Profiles are validated field by field (allowed keys, nickname pattern, avatar, count limits, total = sum). Friend links can only be created by someone who knows the friend code (5 characters from a 31-symbol set, about 28 million combinations, generated with `SecureRandom`). Friend codes can only be created together with your own profile, so codes can't be mass-created. Everything not listed is denied. |
| Rate limiting | The rules reject profile saves less than 10 s apart, and counts cannot go down during the same day. The app saves at most every 30 s. |
| Sanitize forms / block XSS | Names and nicknames are validated against a strict pattern in the app and in the rules. They are shown only in native Android text views, never as HTML. |
| Hash passwords | DozyPal never handles passwords; Firebase handles the anonymous sign-in. |
| CORS / security headers | Not applicable: there is no website or custom server. |
| Debug mode | Release builds set `isDebuggable = false`, and minify/shrink is on. |
| Unused packages / update dependencies | Only Firebase (auth + Firestore). Dependabot checks Gradle and GitHub Actions weekly, plus Dependabot security alerts and automatic security fixes. CI actions are pinned to exact commit SHAs. |
| Exposed files / components | Backups and device-to-device transfer of app data are disabled (`allowBackup=false`, `dataExtractionRules`). Only the launcher activity is exported; the accessibility service can only be bound by the system. |
| Network | HTTPS only (`cleartextTrafficPermitted=false`), trusting system certificates only. |
| Least privilege | The accessibility service is limited to four apps. The overlay needs no "draw over apps" permission. CI uses read-only permissions except for the release job, and the checkout token is not persisted. |
| API key | The Firebase API key is restricted in Google Cloud to the DozyPal Android app (package + signing SHA-1) and to the Firebase APIs it needs. |
