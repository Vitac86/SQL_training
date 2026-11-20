package ru.sqltraining.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubUpdateVersionTest {
    @Test fun identifiesNewerRelease() {
        assertEquals(1, ReleaseVersion.compare("0.4.1", "v0.5.0"))
        assertEquals(1, ReleaseVersion.compare("0.5.9", "v0.6.0"))
    }

    @Test fun detectsSameAndOlderVersion() {
        assertEquals(0, ReleaseVersion.compare("0.5.0", "v0.5.0"))
        assertEquals(-1, ReleaseVersion.compare("0.5.0", "v0.4.9"))
    }

    @Test fun preventsLexicographicVersionMistakes() {
        assertEquals(1, ReleaseVersion.compare("0.9.0", "v0.10.0"))
        assertEquals(1, ReleaseVersion.compare("1.9.9", "2.0.0"))
    }

    @Test fun rejectsMalformedVersion() {
        assertNull(ReleaseVersion.compare("0.5.0", "main"))
        assertNull(ReleaseVersion.compare("unknown", "v0.5.0"))
        assertNull(ReleaseVersion.parse("v0.5.0-preview"))
    }
}
