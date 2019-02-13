package org.nunocky.wavdecode

import java.io.BufferedWriter

interface Stage2Delegate {
    fun onDataReceived(value: Byte)
}

class Stage2(private val bw: BufferedWriter? = null) : Stage1Delegate {
    companion object {
        const val TAG = "Stage2"
        const val THRESHOLD = 4.5
    }

    private var state = 0
    private var oneCount = 0
    private var receivedValue: Byte = 0
    private var bitCount = 0

    private val gf0 = GF(8000, 1000, 32)
    private val gf1 = GF(8000, 2000, 32)

    var delegate: Stage2Delegate? = null

    override fun onSampleBlockReceived(samples: ShortArray) {

//        bw?.write("Stage2: ")
//        samples.forEach {
//            bw?.write("$it ")
//        }
//        bw?.newLine()

        val mag0 = gf0.magnitude(samples)
        val mag1 = gf1.magnitude(samples)

        bw?.write("$TAG: mag $mag0 $mag1")
        bw?.newLine()

        if (THRESHOLD < mag0 && mag0 > mag1) {
            push(0)
        } else if (THRESHOLD < mag1 && mag0 < mag1) {
            push(1)
        }
    }

    private fun push(value: Int) {
        bw?.write("$TAG: push $value")
        bw?.newLine()

        if (state == 0) {
            // 連続する8個の1を待っている
            if (value == 1) {
                oneCount++
                if (oneCount == 8) {
                    state = 1
                    receivedValue = 0
                    bitCount = 0
                }
            } else {
                oneCount = 0
            }
        } else if (state == 1) {
            var v = receivedValue.toInt() and 0xff

            v = v shl 1
            if (value == 1) {
                v = v.or(1)
            }
            receivedValue = v.toByte()
            bitCount++
            if (bitCount == 8) {
                delegate?.onDataReceived(receivedValue)
                bitCount = 0
                receivedValue = 0
            }
        }
    }

}