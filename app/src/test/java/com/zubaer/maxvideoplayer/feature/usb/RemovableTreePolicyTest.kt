package com.zubaer.maxvideoplayer.feature.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovableTreePolicyTest {
    @Test
    fun nonPrimaryExternalStorageTree_isRecognizedAsRemovable() {
        assertTrue(
            RemovableTreePolicy.isLikelyRemovableTree(
                "content://com.android.externalstorage.documents/tree/1234-ABCD%3AMovies",
            ),
        )
    }

    @Test
    fun primaryAndUnrelatedTrees_areNotMislabelledUsb() {
        assertFalse(
            RemovableTreePolicy.isLikelyRemovableTree(
                "content://com.android.externalstorage.documents/tree/primary%3AMovies",
            ),
        )
        assertFalse(
            RemovableTreePolicy.isLikelyRemovableTree(
                "content://com.example.provider/tree/1234-ABCD%3AMovies",
            ),
        )
    }
}
