import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import com.houskov.sporttracker.MainActivity
import com.houskov.sporttracker.R

class LocationUpdatesService: Service() {
    private val mBinder = LocalBinder()
    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private val mChangingConfiguration = false
    private lateinit var mNotificationManager: NotificationManager
    /**
     * Contains parameters used by {@link com.google.android.gms.location.FusedLocationProviderApi}.
     */
    private lateinit var mLocationRequest:LocationRequest
    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    /**
     * Callback for changes in location.
     */
    private lateinit var mLocationCallback:LocationCallback
    private lateinit var mServiceHandler: Handler
    /**
     * The current location.
     */
    private lateinit var mLocation: Location
    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
    private// Extra to help us figure out if we arrived in onStartCommand via the notification or not.
    // The PendingIntent that leads to a call to onStartCommand() in this service.
    // The PendingIntent to launch activity.
    // Set the Channel ID for Android O.
    // Channel ID
    val notification: Notification
        get() {
            val intent = Intent(this, LocationUpdatesService::class.java)
            val text = Utils.getLocationText(mLocation)
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
            val servicePendingIntent = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT)
            val activityPendingIntent = PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java), 0)
            val builder = NotificationCompat.Builder(this)
                    .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                            activityPendingIntent)
                    .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),
                            servicePendingIntent)
                    .setContentText(text)
                    .setContentTitle(Utils.getLocationTitle(this))
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker(text)
                    .setWhen(System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                builder.setChannelId(CHANNEL_ID)
            }
            return builder.build()
        }
    fun onCreate() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object:LocationCallback() {
            fun onLocationResult(locationResult:LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.getLastLocation())
            }
        }
        createLocationRequest()
        getLastLocation()
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.getLooper())
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val name = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel)
        }
    }
    fun onStartCommand(intent:Intent, flags:Int, startId:Int):Int {
        Log.i(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false)
        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification)
        {
            removeLocationUpdates()
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }
    fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }
    fun onBind(intent:Intent):IBinder {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }
    fun onRebind(intent:Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }
    fun onUnbind(intent:Intent):Boolean {
        Log.i(TAG, "Last client unbound from service")
        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this))
        {
            Log.i(TAG, "Starting foreground service")
            /*
       // TODO(developer). If targeting O, use the following code.
       if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
       mNotificationManager.startServiceInForeground(new Intent(this,
       LocationUpdatesService.class), NOTIFICATION_ID, getNotification());
       } else {
       startForeground(NOTIFICATION_ID, getNotification());
       }
       */
            startForeground(NOTIFICATION_ID, notification)
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }
    fun onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null)
    }
    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    fun requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates")
        Utils.setRequestingLocationUpdates(this, true)
        startService(Intent(getApplicationContext(), LocationUpdatesService::class.java))
        try
        {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper())
        }
        catch (unlikely:SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely)
        }
    }
    /**
     * Removes location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    fun removeLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        try
        {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        }
        catch (unlikely:SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely)
        }
    }
    private fun getLastLocation() {
        try
        {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(object:OnCompleteListener<Location>() {
                        fun onComplete(@NonNull task:Task<Location>) {
                            if (task.isSuccessful() && task.getResult() != null)
                            {
                                mLocation = task.getResult()
                            }
                            else
                            {
                                Log.w(TAG, "Failed to get location.")
                            }
                        }
                    })
        }
        catch (unlikely:SecurityException) {
            Log.e(TAG, "Lost location permission." + unlikely)
        }
    }
    private fun onNewLocation(location:Location) {
        Log.i(TAG, "New location: " + location)
        mLocation = location
        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent)
        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this))
        {
            mNotificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    }
    /**
     * Class used for the client Binder. Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder: Binder() {
        internal val service:LocationUpdatesService
            get() {
                return this@LocationUpdatesService
            }
    }
    /**
     * Returns true if this is a foreground service.
     *
     * @param context The {@link Context}.
     */
    fun serviceIsRunningInForeground(context: Context):Boolean {
        val manager = context.getSystemService(
                Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(
                Integer.MAX_VALUE))
        {
            if (getClass().getName().equals(service.service.getClassName()))
            {
                if (service.foreground)
                {
                    return true
                }
            }
        }
        return false
    }
    companion object {
        private val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        private val TAG = LocationUpdatesService::class.java!!.getSimpleName()
        /**
         * The name of the channel for notifications.
         */
        private val CHANNEL_ID = "channel_01"
        internal val ACTION_BROADCAST = PACKAGE_NAME + ".broadcast"
        internal val EXTRA_LOCATION = PACKAGE_NAME + ".location"
        private val EXTRA_STARTED_FROM_NOTIFICATION = (PACKAGE_NAME + ".started_from_notification")
        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private val UPDATE_INTERVAL_IN_MILLISECONDS:Long = 10000
        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private val NOTIFICATION_ID = 12345678
    }
}