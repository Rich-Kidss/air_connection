package com.airconnection.app

object AppConfig {
    /**
     * Your Supabase Project URL.
     * Project ref: cehisgvravkopekjcyzb
     */
    var SUPABASE_URL: String = "https://cehisgvravkopekjcyzb.supabase.co"

    /**
     * Your Supabase Anon / Public Key.
     * Get this from your Supabase Dashboard -> Project Settings -> API.
     */
    var SUPABASE_KEY: String = "sb_publishable_4jG88VqWEgq0IJ1VXyJ5qw_B_HEt85w"

    /**
     * GitHub Username and Repository for the Child download link.
     */
    var GITHUB_USERNAME: String = "Rich-Kidss"
    var GITHUB_REPO: String = "air_connection"
    var GITHUB_BRANCH: String = "main"

    /**
     * GitHub Pages Download URL (Recommended - Renders HTML in browser):
     * https://rich-kidss.github.io/air_connection/download.html
     */
    val childDownloadUrl: String
        get() = "https://$GITHUB_USERNAME.github.io/$GITHUB_REPO/download.html"

    /**
     * Direct Raw APK Download URL:
     * https://raw.githubusercontent.com/Rich-Kidss/air_connection/main/air_connection.apk
     */
    val directApkUrl: String
        get() = "https://raw.githubusercontent.com/$GITHUB_USERNAME/$GITHUB_REPO/$GITHUB_BRANCH/air_connection.apk"
}
