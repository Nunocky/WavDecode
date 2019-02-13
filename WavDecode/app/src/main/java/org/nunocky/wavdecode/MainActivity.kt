package org.nunocky.wavdecode

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.*
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentLinkedQueue


data class Content(var contentType: String = "", var contentLength: Int = 0, var body: ByteArray? = null)

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private val REQUEST_PERMISSION = 1000
    private lateinit var toggleButton: ToggleButton
    private lateinit var textView: TextView
    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvContentType: TextView
    private lateinit var tvContentLength: TextView

    private var contentType = ""
    private var contentLength = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)
        textView = findViewById(R.id.textView)
        imageView = findViewById(R.id.imageView)
        tvContentType = findViewById(R.id.tvContentType)
        tvContentLength = findViewById(R.id.tvContentLength)
        progressBar = findViewById(R.id.progressBar)

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startRecord()
            } else {
                stopRecord()
            }
        }

        if (Build.VERSION.SDK_INT >= 23) {
            checkPermission()
        } else {
            start()
        }
    }

    private fun checkPermission() {
        val check0 = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val check1 = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (check0 && check1) {
            start()
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val check0 = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val check1 = ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)

        if (check0 || check1) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.RECORD_AUDIO
                ), REQUEST_PERMISSION
            )
        } else {
            Toast.makeText(this, "許可がないとアプリが実行できません", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.RECORD_AUDIO
                ), REQUEST_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                //startActivity()
                start()
            } else {
                // それでも拒否されたら
                Toast.makeText(this, "これ以上なにもできません", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun start() {
        toggleButton.isEnabled = true
    }

    private lateinit var audioRecord: AudioRecord
    private var decodeThread: Thread? = null
    private var bw: BufferedWriter? = null

    private fun startRecord() {
        textView.text = "Content"
        imageView.setImageBitmap(null)
        tvContentType.text = "Type:"
        tvContentLength.text = "Length:"
        progressBar.progress = 0


        val fileOutputStream =
            FileOutputStream(Environment.getExternalStorageDirectory().path + "/debug.txt", false)
        val outputStreamWriter = OutputStreamWriter(fileOutputStream, "UTF-8")

        bw = BufferedWriter(outputStreamWriter)
        val fifo = ConcurrentLinkedQueue<Short>()

        val decoder = DecoderRunnable(fifo, bw)
        decodeThread = Thread(decoder)

        val handler = Handler()
        decoder.delegate = object : DecoderRunnableDelegate {

            override fun onHeaderReceived(key: String, value: String) {
//                Log.d(TAG, "Header: $key $value")
                handler.post {
                    if (key == "Content-Type") {
                        contentType = value
                        tvContentType.text = "Type: $value"
                    } else if (key == "Content-Length") {
                        contentLength = value.toInt()
                        tvContentLength.text = "Length: $contentLength"
                        progressBar.max = contentLength
                        progressBar.progress = 0
                    }

                }
            }

            override fun onContentByteReceived(v: Byte) {
                handler.post {
                    progressBar.progress = progressBar.progress + 1
                }
            }

            override fun onContentReceived(byteArray: ByteArray) {
//                Log.d(TAG, "onContentReceived")
                handler.post {
                    try {
                        decodeContent(byteArray)
                    } catch (th: Throwable) {
                        Toast.makeText(this@MainActivity, "decode failed", Toast.LENGTH_SHORT).show()
                    }
                    toggleButton.isChecked = false
                }
            }
        }

        val samplingRate = 8000
        val frameRate = 10
        val oneFrameDataCount = samplingRate / frameRate
        val oneFrameSizeInByte = oneFrameDataCount * 2

        val audioBufferSizeInByte = Math.max(
            oneFrameSizeInByte * 10,
            AudioRecord.getMinBufferSize(samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, // 音声のソース
            samplingRate, // サンプリングレート
            AudioFormat.CHANNEL_IN_MONO, // チャネル設定. MONO and STEREO が全デバイスサポート保障
            AudioFormat.ENCODING_PCM_16BIT, // PCM16が全デバイスサポート保障
            audioBufferSizeInByte
        ) // バッファ

        audioRecord.positionNotificationPeriod = oneFrameDataCount

        val audioDataArray = ShortArray(oneFrameDataCount)

        audioRecord.setRecordPositionUpdateListener(object : AudioRecord.OnRecordPositionUpdateListener {
            override fun onMarkerReached(recorder: AudioRecord) {
                // not called
//                recorder.read(audioDataArray, 0, oneFrameDataCount) // 音声データ読込
//                Log.d(TAG, "onMarkerReached ${audioDataArray.size}")
            }

            override fun onPeriodicNotification(recorder: AudioRecord) {
                recorder.read(audioDataArray, 0, oneFrameDataCount) // 音声データ読込

//                Log.d(TAG, "onPeriodicNotification ${audioDataArray.size}")
                audioDataArray.forEach {
                    //bw.write("${it}\n")
                    fifo.add(it)
                }
            }
        })

        decodeThread?.start()
        audioRecord.startRecording()
        //audioRecord.read(audioDataArray, 0, oneFrameDataCount) // 音声データ読込
    }

    private fun stopRecord() {
        audioRecord.stop()
        decodeThread?.interrupt()
        decodeThread?.join()
        decodeThread = null
        bw?.close()
    }

    private fun decodeContent(body: ByteArray) {
        // コンテントタイプに応じてインテント呼び出し
        if (contentType == "text/plain") {
            textView.text = String(body)
            Toast.makeText(this, "Completed", Toast.LENGTH_SHORT).show()
        } else if (contentType == "image/jpeg") {
            val bitmap = BitmapFactory.decodeByteArray(body, 0, body.size)
            imageView.setImageBitmap(bitmap)
        } else if (contentType == "image/png") {
            val bitmap = BitmapFactory.decodeByteArray(body, 0, body.size)
            imageView.setImageBitmap(bitmap)
        } else {
            Log.d(TAG, "unsupported type")
            //Log.d("MainActivity", content.toString())
            Toast.makeText(this, "Unsupported Type", Toast.LENGTH_SHORT).show()
        }
    }
}
