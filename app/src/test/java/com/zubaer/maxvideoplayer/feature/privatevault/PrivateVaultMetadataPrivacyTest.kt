package com.zubaer.maxvideoplayer.feature.privatevault

import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class PrivateVaultMetadataPrivacyTest {
    @Test
    fun containerNeverStoresOriginalNameOrTitleInPlaintext() {
        val secretName = "TOP_SECRET_PRIVATE_MOVIE_839247.mp4"
        val secretTitle = "TOP_SECRET_PRIVATE_MOVIE_839247"
        val file = File.createTempFile("opaque-vault-", ".maxvault")
        val master = PrivateVaultCrypto.randomKey()
        try {
            PrivateVaultContainerFormat.write(
                input = ByteArrayInputStream(ByteArray(8_193) { (it * 17).toByte() }),
                sourceLength = 8_193L,
                destination = file,
                vaultId = UUID.randomUUID(),
                metadata = PrivateMediaMetadata(secretName, secretTitle, "video/mp4"),
                masterSecret = master,
            )
            val raw = String(file.readBytes(), StandardCharsets.ISO_8859_1)
            assertFalse(raw.contains(secretName))
            assertFalse(raw.contains(secretTitle))
            assertFalse(raw.contains("video/mp4"))
        } finally {
            PrivateVaultCrypto.zero(master)
            file.delete()
        }
    }
}
