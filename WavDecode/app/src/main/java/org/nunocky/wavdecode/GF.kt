package org.nunocky.wavdecode

class GF(sampleFreq: Int, targetFreq: Int, private val numSamples: Int) {
    private var coeff = 0.0

    init {
        val k = (0.5 + (numSamples.toDouble() * targetFreq) / sampleFreq).toInt()
        val omega = (2 * Math.PI / numSamples) * k
        coeff = 2 * Math.cos(omega)
    }

    fun magnitude(samples: ShortArray): Double {
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        for (sample in samples) {
            q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }

        var magnitude = Math.sqrt(q1 * q1 + q2 * q2 - q1 * q2 * coeff)
        magnitude /= 32767
        return magnitude
    }

}