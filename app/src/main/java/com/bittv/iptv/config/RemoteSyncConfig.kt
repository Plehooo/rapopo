package com.bittv.iptv.config

/** Single source of truth for GitHub/FCM remote-update plumbing. */
object RemoteSyncConfig {
    const val GITHUB_REPOSITORY = "Plehooo/ditz"
    const val GITHUB_BRANCH = "main"
    const val PLAYLIST_URL =
        "https://raw.githubusercontent.com/$GITHUB_REPOSITORY/refs/heads/$GITHUB_BRANCH/adit.m3u"
    const val NOTIFICATION_URL =
        "https://raw.githubusercontent.com/$GITHUB_REPOSITORY/refs/heads/$GITHUB_BRANCH/notif.json"

    /** Topic is deliberately generic: one push can invalidate any remote data. */
    const val FCM_TOPIC = "bittv-live-updates"
    const val FCM_EVENT_TYPE = "bittv_remote_sync"

    // Fallback only. FCM is the real-time path; WorkManager is a safety net.
    const val PLAYLIST_FALLBACK_MINUTES = 15L
    const val ANNOUNCEMENT_FALLBACK_MINUTES = 360L
}
