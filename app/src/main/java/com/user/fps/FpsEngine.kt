package com.user.fps

object FpsEngine {

    private const val NS_PER_S = 1_000_000_000L

    fun parseLatency(raw: String): LongArray =
        raw.lineSequence().drop(1).mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size >= 2)
                cols[1].toLongOrNull()?.takeIf { it in 1 until Long.MAX_VALUE }
            else null
        }.toList().toLongArray()

    fun fpsOf(stamps: LongArray): Float {
        if (stamps.size < 2) return 0f
        val now = stamps.last()
        val recent = stamps.filter { now - it <= NS_PER_S }
        if (recent.size < 2) return 0f
        val span = (recent.last() - recent.first()) / 1e9f
        return if (span > 0f) (recent.size - 1) / span else 0f
    }

    fun jankOf(stamps: LongArray, hz: Float): Int {
        if (hz <= 0f || stamps.size < 2) return 0
        val budget = NS_PER_S / hz
        val deltas = stamps.toList().zipWithNext { a, b -> b - a }
        return deltas.count { it > budget * 1.5f } * 100 / deltas.size
    }
}
