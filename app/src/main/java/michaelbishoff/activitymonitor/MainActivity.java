package michaelbishoff.activitymonitor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    // The Lists used for the ListView
    private ArrayList<String> activityList;
    private ArrayAdapter<String> activityListAdapter;

    // The Bounded Service objects
    private ActivityMonitorService.ActivityBinder binder;
    private ActivityMonitorService activityMonitorService;
    private boolean connected = false;

    // The previous date for the
    private String previousDate;

    public static final String OUTPUT_FILENAME = "activities.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        activityList = new ArrayList<>();
        activityListAdapter = new ArrayAdapter<String>(this, R.layout.activity_item, R.id.activity, activityList);

        // Binds the service. Calls the onServiceConnected() method below
        Intent serviceIntent = new Intent(this, ActivityMonitorService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        // Gets the user's activity every 2 minutes
        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask asyncTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (connected) {

                            // Get the activity from the service
                            String activity = activityMonitorService.getActivity();

                            String date = getDate();

                            String formattedActivity = previousDate + " - " + date + "  " + activity;

                            // Adds the time and activity to list
                            activityList.add(0, formattedActivity);
                            updateUI();

                            // Stores the new start date
                            previousDate = date;

                            if (isExternalStorageWritable()) {
                                writeActivity(formattedActivity + "\n");
                            } else {
                                Log.d("FILE-TAG", "External Storage is NOT Writable!");
                            }
                        }
                    }
                });
            }
        };

        // Get the user's activity every 2 minutes
        timer.schedule(asyncTask, 0, 120000);

        // Format the current date
        previousDate = getDate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * The service connection used to call functions in ActivityMonitorService
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            // Gets the binder
            binder = (ActivityMonitorService.ActivityBinder) service;

            // Calls a callback type of function that gets the parent of the binder
            activityMonitorService = binder.getService();

            // Indicates that we have access to the service
            connected = true;

            // Now the Activity is bound to the Service and we can call the
            // public methods defined in the activityMonitorService class

            // Resumes the service
            activityMonitorService.createService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connected = false;
        }
    };

    /**
     * Updates the list view with the new activities
     */
    public void updateUI() {
        ListView listView = (ListView) findViewById(R.id.activityList);
        listView.setAdapter(activityListAdapter);
    }

    /**
     * Gets the date in the format: HH:mm PM
     */
    public String getDate() {
        String am_pm;
        if (Calendar.getInstance().get(Calendar.AM_PM) == Calendar.AM) {
            am_pm = "AM";
        } else {
            am_pm = "PM";
        }

        // Format the current date
        String date = String.format("%d:%02d %s",
                Calendar.getInstance().get(Calendar.HOUR),
                Calendar.getInstance().get(Calendar.MINUTE),
                am_pm);

        return date;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * Writes the user's activity to an output file
     */
    public void writeActivity(String activity) {

        // Initializes a file in the user's public Documents directory
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), OUTPUT_FILENAME);

        try {
            // Creates the output file if it doesn't exist already
            if (!file.exists()) {
                if (file.createNewFile()) {
//                    Log.d("FILE-TAG", "Created file: " + file.getPath());
                } else {
//                    Log.d("FILE-TAG", "COULDN'T CREATE NEW FILE!");
                }
            } else {
//                Log.d("FILE-TAG", file.getName() + " already exists!");
            }

            // Writes the activity to the file. true means to append to the file
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(activity.getBytes());
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
