package org.nunocky.wavdecode

import java.io.BufferedWriter
import java.util.concurrent.ConcurrentLinkedQueue


interface DecoderRunnableDelegate {
    fun onHeaderReceived(key: String, value: String)
    fun onContentReceived(byteArray: ByteArray)
    fun onContentByteReceived(v: Byte)
    //fun onReceived(byteArray: ByteArray)
}

class DecoderRunnable(private val fifo: ConcurrentLinkedQueue<Short>, bw: BufferedWriter? = null) :
    Runnable {

    companion object {
        const val TAG = "DecoderRunnable"
    }

    private val stage1: Stage1 = Stage1(bw)
    private val stage2: Stage2 = Stage2(bw)

    var delegate: DecoderRunnableDelegate? = null

    val bodyByteArray = ArrayList<Byte>()
    var state = 0
    var lineBuffer = ArrayList<Byte>()

    var byteLast: Byte = 0

    init {
        stage1.delegate = stage2

        stage2.delegate = object : Stage2Delegate {
            override fun onDataReceived(value: Byte) {
                if (state == 0) {
                    if (value == '\n'.toByte()) {
                        val line = String(lineBuffer.toByteArray())

                        val keyValue = line.split(":")
                        if (keyValue.size == 2) {
//                            Log.d(TAG, "header: $line")
                            delegate?.onHeaderReceived(keyValue[0], keyValue[1])
                        }

                        lineBuffer.clear()
                    } else {
                        lineBuffer.add(value)
                    }

                    if (byteLast == '\n'.toByte() && value == '\n'.toByte()) {
                        state = 1
                        //Log.d(TAG, "header: start receiving content")
                    }
                } else if (state == 1) {
                    bodyByteArray.add(value)
                    delegate?.onContentByteReceived(value)
                }

                byteLast = value
            }
        }
    }

    override fun run() {
        var done = false
        while (!done) {
            if (Thread.currentThread().isInterrupted) {
                done = true
                continue
            }

            if (stage1.getState() == Stage1.DECODE_COMPLETE) {
                // デコード完了
//                Log.d("Decoder", "Receive Complete")
                done = true
                delegate?.onContentReceived(bodyByteArray.toByteArray())
                bodyByteArray.clear()
                continue
            }

            if (fifo.isNotEmpty()) {
                val value = fifo.poll()
//                bw?.write("$value\n")
                stage1.put(value)
            } else {
                try {
                    Thread.sleep(100)
                } catch (ignored: InterruptedException) {
                    done = true
                    continue
                }
            }
        }
    }

}

