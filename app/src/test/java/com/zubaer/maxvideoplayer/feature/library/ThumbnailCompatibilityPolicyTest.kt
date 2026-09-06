package com.zubaer.maxvideoplayer.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailCompatibilityPolicyTest {
    @Test fun api23Through26UseLegacyRetriever() {
        listOf(23, 24, 25, 26).forEach { api ->
            assertEquals(
                "API $api",
                ThumbnailLoadStrategy.LEGACY_RETRIEVER,
                ThumbnailCompatibilityPolicy.strategy(api),
            )
        }
    }

    @Test fun api27And28UseScaledRetriever() {
        listOf(27, 28).forEach { api ->
            assertEquals(
                "API $api",
                ThumbnailLoadStrategy.SCALED_RETRIEVER,
                ThumbnailCompatibilityPolicy.strategy(api),
            )
        }
    }

    @Test fun api29AndNewerUseContentResolverThumbnailApi() {
        listOf(29, 35, 36).forEach { api ->
            assertEquals(
                "API $api",
                ThumbnailLoadStrategy.CONTENT_RESOLVER,
                ThumbnailCompatibilityPolicy.strategy(api),
            )
        }
    }
}
