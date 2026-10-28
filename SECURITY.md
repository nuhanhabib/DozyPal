# Security

## Reporting a problem

Please report security issues privately via GitHub's **Report a vulnerability** button on the Security tab, rather than opening a public issue.

## Security checklist

| Check | Status in DozyPal |
|---|---|
| Hide API keys / check env variables | No API keys exist. Signing secrets live only in GitHub Actions secrets and are read from environment variables at build time. |
| Secrets in git history | `.gitignore` blocks keystores, `.env` files and `google-services.json`. GitHub secret scanning is enabled on the repo. |
| Debug mode | Release builds set `isDebuggable = false`, and minify/shrink is on. |
| Unused packages / update dependencies | No third-party libraries. Dependabot checks Gradle plugins and GitHub Actions weekly. |
| Exposed files / components | Backups disabled (`allowBackup=false`). The accessibility service can only be bound by the Android system. |
| Least privilege | No internet permission. The accessibility service is limited to Facebook and Instagram packages. The overlay needs no "draw over apps" permission. CI uses read-only permissions except for the release job. |
| Users access own data / proper auth / admin routes / database / rate limiting / hashing | Not applicable yet: there is no server or account. These will be enforced with Firebase security rules when the optional friends feature is added. |
| Sanitize forms / XSS / CORS / security headers | Not applicable yet: no user text input and no web content. Nicknames will be length- and character-limited when friends ship. |
