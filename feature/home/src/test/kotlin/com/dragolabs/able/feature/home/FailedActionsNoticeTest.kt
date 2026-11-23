package com.dragolabs.able.feature.home

import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.model.ActionAuditRecord
import com.dragolabs.able.core.model.ActionExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedActionsNoticeTest {

    private val now = 10L * RECENT_FAILURE_WINDOW_MS
    private val hour = 60L * 60 * 1000

    private fun failure(at: Long) = ActionAuditRecord(
        timestampEpochMs = at,
        actionId = "test",
        status = ActionExecutionStatus.FAILED,
        detailMessage = "failed",
    )

    @Test
    fun noFailuresMeansNoNotice() {
        assertNull(failedActionsNoticeFor(emptyList(), now, dismissedThroughEpochMs = 0))
    }

    @Test
    fun failuresOlderThanWindowAreIgnored() {
        val old = failure(now - RECENT_FAILURE_WINDOW_MS - 1)
        assertNull(failedActionsNoticeFor(listOf(old), now, dismissedThroughEpochMs = 0))
    }

    @Test
    fun hiddenFailuresStayHidden() {
        val failures = listOf(failure(now - hour), failure(now - 2 * hour))
        assertNull(failedActionsNoticeFor(failures, now, dismissedThroughEpochMs = now - hour))
    }

    @Test
    fun newerFailureShowsNoticeAgainWithAllRecentFailures() {
        val failures = listOf(failure(now - 10), failure(now - hour))
        val notice = failedActionsNoticeFor(failures, now, dismissedThroughEpochMs = now - hour)
        assertNotNull(notice)
        assertEquals(2, notice!!.failures.size)
        assertEquals(now - 10, notice.latestEpochMs)
    }

    @Test
    fun shizukuAlertsNeverIncludeFailures() {
        assertTrue(alertsFor(ShizukuConnectionState.READY).isEmpty())
        assertEquals(listOf(HomeSafetyAlert.ShizukuPermissionDenied), alertsFor(ShizukuConnectionState.PERMISSION_DENIED))
    }
}
