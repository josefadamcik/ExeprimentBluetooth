package cz.josefadamcik.experimentbluetooth

import android.app.Service
import android.content.Intent
import android.os.*


/**
 * Similar to {@link android.app.IntentService} but doesn't finishes itself when the queue is empty.
 */
abstract class PersistentIntentService(private val name: String) : Service() {
    private inner class ServiceHandler(private val thread: HandlerThread) : Handler() {
        private var serviceLooper = thread.looper
        override fun handleMessage(msg: Message?) {
            msg?.let {
                onHandleIntent(it.obj as Intent)
            }

        }
    }

    private lateinit var handler: Handler

    /**
     * Runs on background thread and performs operation.
     */
    protected abstract fun onHandleIntent(intent: Intent)



    override fun onCreate() {
        super.onCreate()
        handler = ServiceHandler(HandlerThread("PersistentIntentService[$name]"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val msg = handler.obtainMessage()
        msg.arg1 = startId
        msg.obj = intent
        handler.sendMessage(msg)
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.looper.quit()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }


}



