package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import org.junit.Assert.*
import org.junit.Test

class OfflineLeaseTest {
    private val member = Membership("brand", "Daun Teh", "outlet", "A", Role.CASHIER)
    private val duration = OfflineLeaseRules.MAX_DURATION_MS
    private val lease = DeviceLease("user", "brand", "outlet", "device", Role.CASHIER, 1000, 1000+duration, 5000, 10000, 2)
    private fun valid(value: DeviceLease? = lease, now: ClockReading = ClockReading(5000,10000,2,true)) = OfflineLeaseRules.valid(value,"user",member,"device",now)
    @Test fun maximumMonthLeaseIsBoundedAtExactExpiry() {
        assertTrue(valid(now=ClockReading(5000+duration-1,10000+duration-1,2,true)))
        assertFalse(valid(now=ClockReading(5000+duration,10000+duration,2,true)))
        assertFalse(valid(lease.copy(expiresAtMs=lease.expiresAtMs+1)))
    }
    @Test fun rebootRollbackAndUnsecuredDeviceFailClosed() {
        assertFalse(valid(now=ClockReading(5000,10000,3,true)))
        assertFalse(valid(now=ClockReading(4999,10001,2,true)))
        assertFalse(valid(now=ClockReading(5001,9999,2,true)))
        assertFalse(valid(now=ClockReading(5000,10000,2,false)))
        assertFalse(valid(now=ClockReading(5000,10000,-1,true)))
    }
    @Test fun userDeviceOutletAndRoleMustMatchExactly() {
        assertFalse(valid(lease.copy(userId="other")))
        assertFalse(valid(lease.copy(deviceId="other")))
        assertFalse(valid(lease.copy(outletId="other")))
        assertFalse(valid(lease.copy(role=Role.OWNER)))
        assertFalse(valid(null))
    }
    @Test fun zeroNegativeAndOversizedLeasesCannotUnlock() {
        assertFalse(valid(lease.copy(expiresAtMs=lease.issuedAtMs)))
        assertFalse(valid(lease.copy(issuedAtMs=-1)))
        assertFalse(valid(lease.copy(anchorElapsedMs=-1)))
        assertFalse(valid(lease.copy(expiresAtMs=Long.MAX_VALUE)))
    }
    @Test fun serverDeltaPlusMonotonicClockDoesNotTrustDeviceEpochSkew() {
        assertTrue(valid(lease.copy(issuedAtMs=9000000,expiresAtMs=9000000+duration)))
        assertFalse(valid(now=ClockReading(5001,10000+duration,2,true)))
    }
    @Test fun roleNavigationKeepsCashierOutOfManagementScreens() {
        assertEquals(AccessDestination.POS,RoleNavigation.destinations(Role.CASHIER).first())
        assertFalse(AccessDestination.REPORTS in RoleNavigation.destinations(Role.CASHIER))
        assertFalse(AccessDestination.STAFF in RoleNavigation.destinations(Role.CASHIER))
        assertEquals(AccessDestination.OVERVIEW,RoleNavigation.destinations(Role.OWNER).first())
        assertTrue(AccessDestination.POS in RoleNavigation.destinations(Role.OWNER))
        assertTrue(AccessDestination.HISTORY in RoleNavigation.destinations(Role.OWNER))
        assertFalse(AccessDestination.POS in RoleNavigation.destinations(Role.MANAGER))
        assertFalse(AccessDestination.HISTORY in RoleNavigation.destinations(Role.MANAGER))
    }
}
