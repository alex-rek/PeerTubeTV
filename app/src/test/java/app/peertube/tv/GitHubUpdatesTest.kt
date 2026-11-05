package app.peertube.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdatesTest {
    @Test
    fun comparesNumericVersionComponents() {
        assertTrue(GitHubUpdates.compareVersions("1.0.10", "1.0.9") > 0)
        assertEquals(0, GitHubUpdates.compareVersions("v1.2.0", "1.2"))
        assertTrue(GitHubUpdates.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun onlyAllowsHttpsAssetsFromGithubDownloadHosts() {
        assertTrue(GitHubUpdates.isAllowedDownloadUrl("https://github.com/alex-rek/PeerTubeTV/releases/download/v1/PeerTubeTV.apk"))
        assertTrue(GitHubUpdates.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/file.apk"))
        assertTrue(GitHubUpdates.isAllowedDownloadUrl("https://objects.githubusercontent.com/file.apk"))
        assertFalse(GitHubUpdates.isAllowedDownloadUrl("http://github.com/file.apk"))
        assertFalse(GitHubUpdates.isAllowedDownloadUrl("https://github.com.evil.example/file.apk"))
        assertFalse(GitHubUpdates.isAllowedDownloadUrl("https://attacker.example/file.apk"))
        assertFalse(GitHubUpdates.isAllowedDownloadUrl("https://user@github.com/file.apk"))
    }

    @Test
    fun acceptsOnlyWellFormedSha256Digests() {
        val digest = "a".repeat(64)
        assertEquals(digest, GitHubUpdates.normalizeSha256("sha256:$digest"))
        assertEquals(null, GitHubUpdates.normalizeSha256("sha256:abcd"))
    }
}
