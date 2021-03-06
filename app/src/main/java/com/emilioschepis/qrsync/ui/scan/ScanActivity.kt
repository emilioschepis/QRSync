package com.emilioschepis.qrsync.ui.scan

import android.arch.lifecycle.Observer
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.PreferenceManager
import android.widget.ToggleButton
import com.emilioschepis.qrsync.R
import com.emilioschepis.qrsync.extension.snackbarError
import com.emilioschepis.qrsync.model.QSError
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.Flash
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.atomic.AtomicBoolean


class ScanActivity : AppCompatActivity() {

    private val viewModel by viewModel<ScanViewModel>()
    private val processing = AtomicBoolean(false)

    private val root by lazy { findViewById<CoordinatorLayout>(R.id.scan_root_cdl) }
    private val camera by lazy { findViewById<CameraView>(R.id.scan_cmv) }
    private val flashToggle by lazy { findViewById<ToggleButton>(R.id.scan_flash_tgb) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        camera.addFrameProcessor { frame ->
            if (!processing.get()) {
                processing.compareAndSet(false, true)
                viewModel.scanImage(frame.data, frame.size.width, frame.size.height).observe(this@ScanActivity, Observer {
                    it?.fold(this@ScanActivity::onImageScanError, this@ScanActivity::onImageScanSuccess)
                })
            }
        }

        flashToggle.setOnCheckedChangeListener { view, isChecked ->
            camera.flash = if (isChecked) Flash.TORCH else Flash.OFF

            val drawableRes = if (isChecked) R.drawable.ic_flash_on_white_24dp else R.drawable.ic_flash_off_white_24dp
            view.setCompoundDrawablesWithIntrinsicBounds(getDrawable(drawableRes), null, null, null)
        }
    }

    override fun onResume() {
        super.onResume()
        camera.start()
    }

    override fun onPause() {
        camera.stop()
        super.onPause()
    }

    override fun onDestroy() {
        camera.destroy()
        super.onDestroy()
    }

    private fun uploadCodes(barcodes: List<FirebaseVisionBarcode>) {
        viewModel.uploadCodes(barcodes).observe(this, Observer {
            it?.fold(this::onUploadSuccess, this::onUploadError)
        })
    }

    private fun onImageScanError(error: QSError) {
        processing.compareAndSet(true, false)
        snackbarError(root, error)
    }

    private fun onImageScanSuccess(barcodes: List<FirebaseVisionBarcode>) {
        if (barcodes.isEmpty()) {
            processing.compareAndSet(true, false)
        } else {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("key_beep_on_code", true)
                    .run {
                        if (this) {
                            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                    .startTone(ToneGenerator.TONE_PROP_BEEP2)
                        }
                    }

            camera.stop()
            uploadCodes(barcodes)
        }
    }

    private fun onUploadError(error: QSError) {
        snackbarError(root, error)
    }

    private fun onUploadSuccess() {
        finish()
    }
}
