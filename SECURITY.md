# Security

## Reporting a problem

Please report security issues privately via GitHub's **Report a vulnerability** button on the Security tab, rather than opening a public issue.

## Security checklist

| Check | Status in DozyPal |
|---|---|
| Hide API keys / check env variables | Signing secrets and the Firebase config (`google-services.json`) live only in GitHub Actions secrets and are written to the build machine at build time, then deleted. |
| Secrets in git history | `.gitignore` blocks keystores, `.env` files and `google-services.json`. GitHub secret scanning is enabled on the repo. |
| Proper authentication | Firebase Authentication (Google sign-in via Credential Manager, or anonymous). Every database rule requires a signed-in user. |
| Users access own data | [`firestore.rules`](firestore.rules): only you can write your profile; it is readable only by you and your friends; listing all users is blocked. |
| Protect admin routes | There are no admin endpoints; everything not explicitly allowed in the rules is denied. |
| Secure database | Profiles are validated field by field (allowed keys, nickname pattern, avatar, count limits, total = sum). Friend links can only be created by someone who knows the friend code. |
| Rate limiting | The rules reject profile saves less than 10 s apart, and counts cannot go down during the same day. The app saves at most every 30 s. |
| Sanitize forms / block XSS | Names and nicknames are validated against a strict pattern in the app and in the rules. They are shown only in native Android text views, never as HTML. |
| Hash passwords | DozyPal never handles passwords; Google/Firebase handle sign-in. |
| CORS / security headers | Not applicable: there is no website or custom server. |
| Debug mode | Release builds set `isDebuggable = false`, and minify/shrink is on. |
| Unused packages / update dependencies | Only Firebase (auth + Firestore), Credential Manager / Google ID and ZXing core. Dependabot checks Gradle and GitHub Actions weekly. |
| Exposed files / components | Backups disabled (`allowBackup=false`). Only the launcher activity is exported; the accessibility service can only be bound by the system. |
| Least privilege | The accessibility service is limited to four apps. The overlay needs no "draw over apps" permission. CI uses read-only permissions except for the release job. |
