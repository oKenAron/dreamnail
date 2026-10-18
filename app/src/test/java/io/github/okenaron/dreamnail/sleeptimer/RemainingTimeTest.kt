package io.github.okenaron.dreamnail.sleeptimer

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingTimeTest {

    @Test
    fun `普通情况`() {
        assertEquals("1:30", formatRemaining(90_000))
    }

    @Test
    fun `秒数往上取整`() {
        assertEquals("1:00", formatRemaining(59_001))
        assertEquals("0:01", formatRemaining(1))
    }

    @Test
    fun `整分钟不进位`() {
        assertEquals("1:00", formatRemaining(60_000))
    }

    @Test
    fun `到点显示 0_00`() {
        assertEquals("0:00", formatRemaining(0))
    }

    @Test
    fun `已经过了结束时刻也不显示负数`() {
        assertEquals("0:00", formatRemaining(-500))
    }

    @Test
    fun `最长 120 分钟不换成小时`() {
        assertEquals("120:00", formatRemaining(120 * 60_000L))
    }
}
