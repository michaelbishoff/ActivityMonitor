package michaelbishoff.activitymonitor;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class ActivityMonitorService extends Service implements SensorEventListener, LocationListener {

    // Binds the MainActivity and the Service
    private ActivityBinder activityBinder = new ActivityBinder();


    // Accelerometer objects
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final int SENSOR_DELAY = 100; // # of microseconds

    // The average angle of the phone and the number of angles used in the average
    double averageAngle = 0;
    int averageAngleCount = 0;


    // Location objects
    private LocationManager locationManager;
    private Location previousLocation;
    private Location previousGpsLocation;

    // The 6, 20 second intervals for determining if the user is walking
    boolean[] isWalkingInterval = new boolean[6];
    int isWalkingIntervalIndex = 0;
    // The number of intervals that the user is walking, out of 6 total
    int numIntervalsWalking = 0;

    // Earth circumference in meters
    public static final double EARTH_CIRCUMFERENCE = 6378137;
    // The minimum distance the user needs to walk to be registered as "walking"
    public static final double MIN_WALK_DISTANCE = 0.5;


    public ActivityMonitorService() { }

    /* Public Methods called from MainActivity */

    /**
     * Once the service is bounded, this is called to start collecting
     * data from the sensor and track the user's activity.
     */
    public void createService() {

        // Get access to the Sensor Service
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Get access to the Sensor
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Ask to sample the accelerometer at a normal rate
        // and specify the maximum report latency (DELAY)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SENSOR_DELAY);

        // Get access to the location manager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Need to check if we have permission to get location information (auto completed from
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // Need to request GPS and Network Provider so we can call getLastKnownLocation()
        // GPS or Network provider (indoors) for triangulation, minTime in miliseconds between
        // updates 0 is fast as possible, minDistance how far the user has to move before we read
        // another value 0 because we don't move a lot
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);


        // Gets the user's activity every 2 minutes
        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask asyncTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        // Need to check if we have permission to get location information (auto completed from
                        // locationManager_.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0, this); vv
                        if (ActivityCompat.checkSelfPermission(ActivityMonitorService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                && ActivityCompat.checkSelfPermission(ActivityMonitorService.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }

                        Location networkLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);


                        if (gpsLoc == null || networkLoc == null) {
                            Log.d("LOC-TAG", "GPS or Network is null");
                        }

//                        Log.d("LOC-TAG", "Last Location GPS = " + gpsLoc);
//                        Log.d("LOC-TAG", "Last Location Net = " + networkLoc);

//                        Log.d("LOC-TAG", String.format("Loc time GPS: %d", gpsLoc.getTime()));
//                        Log.d("LOC-TAG", String.format("Loc time Net: %d", networkLoc.getTime()));


                        // Set the previous location if it is not already set
                        if (previousLocation == null) {

                            // Sets the initial previous locaiotn to the freshest location
                            if (gpsLoc.getTime() > networkLoc.getTime()) {
                                previousLocation = gpsLoc;
                            } else {
                                previousLocation = networkLoc;
                            }

                            // Record the previous GPS location so we can see we are inside or not
                            previousGpsLocation = gpsLoc;

                            return;
                        }


                        // The current location
                        Location loc;

                        // If GPS location is not changing, we are inside. So take the network location
                        if (previousGpsLocation.getTime() == gpsLoc.getTime()) {
                            loc = networkLoc;
                        }
                        // Use the GPS location otherwise because it's more accurate
                        else {
                            loc = gpsLoc;
                        }

                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();

                        double prevLat = previousLocation.getLatitude();
                        double prevLng = previousLocation.getLongitude();

                        // Calculates the Great Circle Distance for the
                        // true distance that the user walked

                        double radLat = Math.toRadians(prevLat - lat);
                        double radLng = Math.toRadians(prevLng - lng);

                        double a = Math.pow(Math.sin(radLat / 2), 2)
                                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(prevLat)) * Math.pow(Math.sin(radLng / 2), 2);
                        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

                        double distance = EARTH_CIRCUMFERENCE * c;

//                        Log.d("LOC-TAG", String.format("Distance: %.10f", distance));

                        if (distance > MIN_WALK_DISTANCE) {
                            // They weren't walking (at this interval), and are currently walking.
                            // Increase the number of walking indicies in the 2 minute interval
                            if (!isWalkingInterval[isWalkingIntervalIndex]) {
                                isWalkingInterval[isWalkingIntervalIndex] = true;
                                numIntervalsWalking++;
                            }

                        } else {
                            // They were walking (at this index), and are currently NOT walking
                            // Decrease the number of walking indicies in the 2 minute interval
                            if (isWalkingInterval[isWalkingIntervalIndex]) {
                                isWalkingInterval[isWalkingIntervalIndex] = false;
                                numIntervalsWalking--;
                            }
                        }

                        // Increments to the next interval and loops around the array
                        isWalkingIntervalIndex = (isWalkingIntervalIndex + 1) % 6;

                        // Record the previous locations
                        previousLocation = loc;
                        previousGpsLocation = gpsLoc;
                    }
                });
            }
        };

        // Get the location every 20 seconds
        timer.schedule(asyncTask, 0, 20000);
    }

    /**
     * Returns the user's activity that we tracked
     */
    public String getActivity() {

        String activity;

        // If most of the 20 sec intervals are walking in the 2 minutes, then they're walking
        if (numIntervalsWalking > 3) {
            activity = "Walking";
        } else if (averageAngle > 65.0 && averageAngle < 115.0) {
            activity = "Sleeping";
        } else {
            activity = "Sitting";
        }

        // Resets the average angle for the 2 minutes interval
        averageAngle = 0;
        averageAngleCount = 0;

        return activity;
    }


    /* Accelerometer Methods */

    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            // Get the accelerometer data from the sensor
            float acclX = event.values[0];
            float acclY = event.values[1];
            float acclZ = event.values[2];

            // Calculates the Y angle of the device in degrees
            double yAngle = Math.acos(acclY / Math.sqrt(Math.pow(acclX, 2)
                                                + Math.pow(acclY, 2)
                                                + Math.pow(acclZ, 2))) * 180 / Math.PI;

            // Keeps a running average of the angle
            averageAngle += (yAngle - averageAngle) / ++averageAngleCount;

//            Log.d("MYTAG", String.format("x: %f\ny: %f\nz: %f", acclX, acclY, acclZ));
//            Log.d("MYTAG", String.format("Y Angle: %f", yAngle));
//            Log.d("MYTAG", String.format("Average Angle: %f", averageAngle));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }


    /* Location Services Methods */
    /*
       Only "implemented" so that we have a LocationListener. We use getLastKnownLocation which
       requires there to be a listener listening
     */
    @Override
    public void onLocationChanged(Location location) {
        // Assigns the previous location initially, then uses the 20 second
        // interval for all following location data
        if (previousLocation == null) {
            previousLocation = location;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }


    /* Bounded Service Methods */
    @Override
    public IBinder onBind(Intent intent) {
        return activityBinder;
    }

    /**
     *  Sends back the ActivityMonitorService to the Activity
     *  Enables us to call a funciton on the ActivityMonitorService from another class.
     *  Like a call back, but using an object to call the method from the other class
     */
    public class ActivityBinder extends Binder {
        public ActivityMonitorService getService() {
            return ActivityMonitorService.this;
        }
    }
}
