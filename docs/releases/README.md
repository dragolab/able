# Release manifest

`latest.json` describes the newest Able release in the same shape as a GitHub release object
(one-element array). The in-app updater reads it from `raw.githubusercontent.com` before the
GitHub REST API, which has a 60 requests/hour per IP limit (ADR-054).

Update it with every release, after the GitHub release and its APK asset are published:
`tag_name`, `name`, `body`, `published_at`, `html_url` and the APK asset's `name`, `size` and
`browser_download_url` must match the published release.
