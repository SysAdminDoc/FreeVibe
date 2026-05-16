package com.freevibe.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap N-2: server-side `admin: true` Firebase Custom Claim is authoritative.
 * Device-ID hash and UID allowlist are migration fallbacks only. These tests pin the
 * precedence rule so the migration can be tightened (or the fallbacks removed) with
 * confidence.
 */
class AdminPrecedenceTest {

    private val sampleHash = "70221777b62eabc52f5d0625fe7fd27f6a96f1a314231f0a33e7db98cb7da49b"
    private val knownHashes = setOf(sampleHash)
    private val knownUids = setOf("uid-admin-1")

    @Test
    fun `claim alone grants admin even when fallback lists are empty`() {
        assertTrue(
            computeIsAdmin(
                adminFromClaims = true,
                deviceIdHash = "any-non-listed-hash",
                currentUserId = "not-listed-uid",
                adminDeviceIdHashes = emptySet(),
                adminUserIds = emptySet(),
            )
        )
    }

    @Test
    fun `device-id hash fallback grants admin when claim is absent`() {
        assertTrue(
            computeIsAdmin(
                adminFromClaims = false,
                deviceIdHash = sampleHash,
                currentUserId = "not-listed-uid",
                adminDeviceIdHashes = knownHashes,
                adminUserIds = emptySet(),
            )
        )
    }

    @Test
    fun `uid allowlist fallback grants admin when claim is absent`() {
        assertTrue(
            computeIsAdmin(
                adminFromClaims = false,
                deviceIdHash = "not-listed-hash",
                currentUserId = "uid-admin-1",
                adminDeviceIdHashes = knownHashes,
                adminUserIds = knownUids,
            )
        )
    }

    @Test
    fun `non-admin with no fallback match returns false`() {
        assertFalse(
            computeIsAdmin(
                adminFromClaims = false,
                deviceIdHash = "not-listed-hash",
                currentUserId = "not-listed-uid",
                adminDeviceIdHashes = knownHashes,
                adminUserIds = knownUids,
            )
        )
    }

    @Test
    fun `claim true wins even if hash and uid lists are empty`() {
        // Once Custom Claims are fully deployed the fallback sets become empty.
        // Make sure the claim-only path still grants admin so we can drop the fallback safely.
        assertTrue(
            computeIsAdmin(
                adminFromClaims = true,
                deviceIdHash = "any-hash",
                currentUserId = "any-uid",
                adminDeviceIdHashes = emptySet(),
                adminUserIds = emptySet(),
            )
        )
    }

    @Test
    fun `empty fallback sets do not grant admin when claim is false`() {
        // Negative side of the above: once Custom Claims are deployed and fallbacks are empty,
        // a user without the claim must not get admin.
        assertFalse(
            computeIsAdmin(
                adminFromClaims = false,
                deviceIdHash = "any-hash",
                currentUserId = "any-uid",
                adminDeviceIdHashes = emptySet(),
                adminUserIds = emptySet(),
            )
        )
    }
}
