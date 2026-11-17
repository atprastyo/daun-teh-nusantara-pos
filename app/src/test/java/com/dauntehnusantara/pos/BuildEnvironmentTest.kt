package com.dauntehnusantara.pos

import org.junit.Assert.*
import org.junit.Test

class BuildEnvironmentTest {
    @Test fun appDataIsIsolatedByEnvironmentAndProductionKeepsBrandPackage() {
        when(BuildConfig.FLAVOR) {
            "dev" -> assertEquals("com.dauntehnusantara.pos.dev",BuildConfig.APPLICATION_ID)
            "prod" -> assertEquals("com.dauntehnusantara.pos",BuildConfig.APPLICATION_ID)
            else -> fail("Expected explicit dev/prod environment")
        }
    }
    @Test fun namespaceDoesNotChangeWithInstallPackage() {
        assertEquals("com.dauntehnusantara.pos",BuildConfig::class.java.`package`!!.name)
    }
}
