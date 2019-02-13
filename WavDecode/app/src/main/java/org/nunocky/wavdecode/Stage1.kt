package org.nunocky.wavdecode

import java.io.BufferedWriter

interface Stage1Delegate {
    fun onSampleBlockReceived(samples: ShortArray)
}

class Stage1(private val bw: BufferedWriter? = null) {
    companion object {
        const val TAG = "Stage1"
        const val DECODE_COMPLETE = 3
        const val SAMPLE_BLOCK_NUM = 32
        const val STATE0_LEVEL_THRESHOLD = (32768 * 0.90).toInt()
        const val STATE2_POW_THRESHOLD = 1000.0
    }

    private var state = 0

    fun getState(): Int {
        return state
    }

    var delegate: Stage1Delegate? = null

    private var sampleCount = 0
    private val samples = ShortArray(SAMPLE_BLOCK_NUM)
    private var samplePrev: Short = 0

    fun put(sampleCurrent: Short) {
        when (state) {
            0 -> put_state0(sampleCurrent)
            1 -> put_state1(sampleCurrent)
            2 -> put_state2(sampleCurrent)
            3 -> {
                // 無音 -> データ転送完了状態
            }
        }

        samplePrev = sampleCurrent
    }

    private fun put_state0(sampleCurrent: Short) {
        // 絶対値が一定値を超えたら次へ
        val v = if (sampleCurrent < 0) (-sampleCurrent).toShort() else sampleCurrent

        if (STATE0_LEVEL_THRESHOLD < v) {
            state = 1

            bw?.write("$TAG: state0 v=$v")
            bw?.newLine()
            bw?.write("$TAG:  state0 -> state1")
            bw?.newLine()
        }
    }

    private fun put_state1(sampleCurrent: Short) {
        // 位相の調整 : 値が負から正に変わる
        if (samplePrev < 0 && 0 < sampleCurrent) {
            state = 2
            sampleCount = 0
            samples[sampleCount++] = sampleCurrent

            bw?.write("$TAG: state1 $samplePrev -> $sampleCurrent")
            bw?.newLine()
            bw?.write("$TAG: state1 -> state2")
            bw?.newLine()
        }
    }

    private fun put_state2(sampleCurrent: Short) {
        // コンテント受信
        if (sampleCount == -1) {
            // 位相の調整: sampleCurrent < 0のときは1サンプル分読み飛ばす
            sampleCount = 0
            if (0 < sampleCurrent) {
                samples[sampleCount++] = sampleCurrent
            }
        } else {
            samples[sampleCount++] = sampleCurrent
        }

        if (sampleCount == SAMPLE_BLOCK_NUM) {

            // pow ... 音の大きさ 一定値以下なら音が途切れたと判断
            val pow = calcPow(samples)
            bw?.write("$TAG: state2 pow $pow")
            bw?.newLine()

            if (pow < STATE2_POW_THRESHOLD) {
                // 音が途切れた
                state = 3
                bw?.write("$TAG: state2 -> state3")
                bw?.newLine()
            } else {
                // 32サンプルを次段に送る
                delegate?.onSampleBlockReceived(samples)
                sampleCount = 0

                // 位相の調整
                if (0 <= samples[samples.size - 1]) {
                    samples[sampleCount++] = samples[samples.size - 1]
                } else {
                    sampleCount = -1
                }
            }
        }
    }

    private fun calcPow(blk: ShortArray): Double {
        var pow = 0.0
        blk.forEach {
            pow += it * it
        }
        pow = Math.sqrt(pow) / blk.size
        return pow
    }
}