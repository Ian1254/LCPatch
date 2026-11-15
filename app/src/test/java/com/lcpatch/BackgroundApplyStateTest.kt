package com.lcpatch

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class BackgroundApplyStateTest {
    @Test fun simultaneousStartsAcceptExactlyOnePackAndReleaseAfterFailure() {
        BackgroundApplyState.finish("reset", false)
        val gate=CountDownLatch(1)
        val accepted=AtomicInteger()
        val threads=(1..16).map { n -> Thread {
            gate.await()
            if(BackgroundApplyState.begin("pack-$n")) accepted.incrementAndGet()
        }.apply {start()} }
        gate.countDown()
        threads.forEach {it.join()}
        assertEquals(1,accepted.get())
        val path=BackgroundApplyState.state.value.packPath
        BackgroundApplyState.prepared("other-pack")
        assertEquals(path,BackgroundApplyState.state.value.packPath)
        assertTrue(BackgroundApplyState.state.value.busy)
        BackgroundApplyState.finish("failed",true)
        assertFalse(BackgroundApplyState.state.value.busy)
        assertTrue(BackgroundApplyState.state.value.failed)
        assertTrue(BackgroundApplyState.begin("retry-pack"))
        assertEquals("retry-pack",BackgroundApplyState.state.value.packPath)
        BackgroundApplyState.finish("done",false)
    }

    @Test fun preparationIsWaitingAndOnlyWritingLocksOperations() {
        BackgroundApplyState.finish("reset",false)
        BackgroundApplyState.prepared("selected")
        assertTrue(BackgroundApplyState.state.value.waiting)
        assertFalse(BackgroundApplyState.state.value.busy)
        assertTrue(BackgroundApplyState.begin("selected"))
        assertFalse(BackgroundApplyState.state.value.waiting)
        BackgroundApplyState.progress(ApplyProgress("write",17,31))
        assertEquals(17,BackgroundApplyState.state.value.progress?.current)
        val revision=BackgroundApplyState.state.value.revision
        BackgroundApplyState.finish("done",false)
        assertEquals(revision+1,BackgroundApplyState.state.value.revision)
        assertFalse(BackgroundApplyState.state.value.busy)
    }
}
