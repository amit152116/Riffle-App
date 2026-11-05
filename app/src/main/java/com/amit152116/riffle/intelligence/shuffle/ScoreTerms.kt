package com.amit152116.riffle.intelligence.shuffle

import com.amit152116.riffle.intelligence.model.EligibleRecording
import com.amit152116.riffle.intelligence.model.ShuffleConfig
import com.amit152116.riffle.intelligence.similarity.KeyDistance
import kotlin.math.abs

/** One recording in the shuffle pool, with the representative Track's id and artist tag. */
class PoolEntry(val recording: EligibleRecording, val trackId: Long, val artist: String?) {
    val audioContentId: Long get() = recording.audioContentId
}

class StepContext(
    val current: PoolEntry,
    val recentArtists: List<String?>,
    val bpmSpread: Double,
    val config: ShuffleConfig
)

/** `skipped` = the inputs were missing, so the term contributed 0 by omission (not by measurement). */
class TermScore(val name: String, val value: Double, val skipped: Boolean = false)

interface ScoreTerm {
    val name: String
    fun evaluate(ctx: StepContext, candidate: PoolEntry, similarity: Double): TermScore
}

object SimilarityTerm : ScoreTerm {
    override val name = "similarity"
    override fun evaluate(ctx: StepContext, candidate: PoolEntry, similarity: Double) = TermScore(name, similarity)
}

object ArtistRepeatTerm : ScoreTerm {
    override val name = "artistRepeat"
    override fun evaluate(ctx: StepContext, candidate: PoolEntry, similarity: Double): TermScore {
        val artist = candidate.artist ?: return TermScore(name, 0.0, skipped = true)
        return TermScore(name, if (artist in ctx.recentArtists) ctx.config.artistRepeatPenalty else 0.0)
    }
}

object BpmJumpTerm : ScoreTerm {
    override val name = "bpmJump"
    override fun evaluate(ctx: StepContext, candidate: PoolEntry, similarity: Double): TermScore {
        val cur = ctx.current.recording
        val cand = candidate.recording
        if (!cur.hasValidBpm || !cand.hasValidBpm) return TermScore(name, 0.0, skipped = true)
        val jump = abs(cur.bpm!! - cand.bpm!!)
        return TermScore(name, if (jump > ctx.bpmSpread * ctx.config.bpmJumpFactor) ctx.config.bpmJumpPenalty else 0.0)
    }
}

/** A bonus (negative), not a penalty: adjacent keys make a smoother transition. */
object KeyHarmonyTerm : ScoreTerm {
    override val name = "keyHarmonyBonus"
    override fun evaluate(ctx: StepContext, candidate: PoolEntry, similarity: Double): TermScore {
        val cur = ctx.current.recording
        val cand = candidate.recording
        val d = KeyDistance.distance(cur.keyName, cur.scale, cand.keyName, cand.scale)
            ?: return TermScore(name, 0.0, skipped = true)
        return TermScore(name, if (d <= ctx.config.keyHarmonyMaxDistance) -ctx.config.keyHarmonyBonus else 0.0)
    }
}
