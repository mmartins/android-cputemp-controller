package edu.brown.cs.systems.cputemp.services;

import java.io.IOException;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.Shell;

import edu.brown.cs.systems.cputemp.ui.MainActivity;

public class TemperatureControllerService extends Service {

    private static final String TAG = "TemperatureControllerService";
    private boolean enabled;
    private long maxTemperature = DEFAULT_TEMPERATURE;
    private int injectionProb = DEFAULT_INJECTION_PROBABILITY;
    private int taskInterval = 60;

    private static final String SYSFS_ENABLE_PATH = "/sys/....";
    private static final String SYSFS_CURRENT_TEMP_PATH = "/sys/....";
    private static final String SYSFS_INJECTION_PROB_PATH = "/sys/....";

    private static final int DEFAULT_INJECTION_PROBABILITY = 0;
    private static final int INJECTION_INCREASE_STEP = 2;
    private static final int INJECTION_DECREASE_STEP = 1;

    public static final int DEFAULT_TEMPERATURE = 60;
    public static final int MAX_TEMPERATURE = 100;
    public static final int MIN_TEMPERATURE = 0;

    private Shell shell;
    private String cmdReturn;

    private PendingIntent sender; // recurring calibration task
    public static final String TEMP_CONTROL_ACTION = "edu.brown.cs.systems.cputemp.TEMP_CONTROL";
    public static final String UPDATE_UI_ACTION = "edu.brown.cs.systems.cputemp.UPDATE_UI";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            shell = RootTools.getShell(true);
        } catch (Exception e) {
            Log.e(TAG, "Can't access shell. Service stopped.");
            e.printStackTrace();
            Toast.makeText(this, "Can't access shell", Toast.LENGTH_SHORT)
                    .show();
            stopSelf();
        }

        enabled = isIdleInjecting();
        maxTemperature = getMaxTemperature();
        Log.d(TAG, "onCreate()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (shell != null) {
            try {
                shell.close();
            } catch (IOException e) {
                Log.e(TAG, "Can't close shell");
                e.printStackTrace();
            }
        }

        Log.d(TAG, "onDestroy()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (intent.getAction().matches(TEMP_CONTROL_ACTION)) {
            enabled = intent.getBooleanExtra("enabled", false);
            maxTemperature = intent.getIntExtra("maxCpuTemp",
                    DEFAULT_TEMPERATURE);

            if (enabled)
                startTemperatureController(maxTemperature);
            else
                stopTemperatureController();
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    public boolean isIdleInjecting() {
        String ans = readSysFs(SYSFS_ENABLE_PATH);
        return ((ans != null && ans.equals("1")) ? true : false);
    }

    private boolean switchIdleInjection(boolean onOff) {
        boolean ret = false;
        if (ret = writeSysFs(SYSFS_ENABLE_PATH, onOff ? "1" : "0"))
            enabled = onOff;
        return ret;
    }

    public long getCurrentTemperature() {
        String ans = readSysFs(SYSFS_CURRENT_TEMP_PATH);
        return (ans != null ? Long.parseLong(ans) : -1L);
    }

    public long getMaxTemperature() {
        return maxTemperature;
    }

    public int getInjectionProbability() {
        String ans = readSysFs(SYSFS_INJECTION_PROB_PATH);
        return (ans != null ? Integer.parseInt(ans) : 0);
    }

    private void setInjectionProbability(int injectProb) {
        assert injectionProb >= 0 : injectionProb;
        if (writeSysFs(SYSFS_INJECTION_PROB_PATH,
                Integer.toString(injectionProb))) {
            this.injectionProb = injectProb;
        } else {
            Toast.makeText(TemperatureControllerService.this,
                    "Can't change injection probability", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private String readSysFs(String path) {
        if (RootTools.isRootAvailable()) {
            if (RootTools.isAccessGiven()) {
                CommandCapture command = new CommandCapture(0, "cat " + path) {
                    @Override
                    public void output(int id, String line) {
                        cmdReturn = line;
                    }
                };

                try {
                    shell.add(command).waitForFinish();
                    // If cat succeeds, it returns 0; 1 otherwise
                    if (command.exitCode() != 0)
                        return null;
                } catch (Exception e) {
                    Log.e(TAG,
                            "Error running shell command: "
                                    + command.getCommand());
                }
            } else {
                Toast.makeText(TemperatureControllerService.this,
                        "Controller requires root access to work",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(TemperatureControllerService.this,
                    "Controller only works on rooted devices",
                    Toast.LENGTH_SHORT).show();
        }

        return cmdReturn;
    }

    private boolean writeSysFs(String path, String value) {
        if (RootTools.isRootAvailable()) {
            if (RootTools.isAccessGiven()) {
                CommandCapture command = new CommandCapture(0, "echo " + value
                        + " > " + path) {
                    @Override
                    public void output(int id, String line) {
                        cmdReturn = line;
                    }
                };

                try {
                    shell.add(command).waitForFinish();
                } catch (Exception e) {
                    Log.e(TAG,
                            "Error running shell command: "
                                    + command.getCommand());
                    Toast.makeText(TemperatureControllerService.this,
                            "Shell command failed", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(TemperatureControllerService.this,
                        "Controller requires root access to work",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(TemperatureControllerService.this,
                    "Controller only works on rooted devices",
                    Toast.LENGTH_SHORT).show();
        }

        return (cmdReturn.equals("0") ? true : false);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    private void startTemperatureController(long tempThreshold) {
        Log.d(TAG, "startTemperatureController");

        switchIdleInjection(enabled);
        maxTemperature = tempThreshold;

        Intent intent = new Intent(TemperatureControllerService.this,
                AlarmReceiver.class);
        sender = PendingIntent.getBroadcast(TemperatureControllerService.this,
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                taskInterval, sender);

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(TemperatureControllerService.this);
        SharedPreferences.Editor editor = preferences.edit();

        // Make sure services won't be running next time we open the application
        editor.putBoolean("enabled", true);
        editor.commit();

    }

    private void stopTemperatureController() {
        Log.d(TAG, "stopTemperatureController");
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(TemperatureControllerService.this);
        SharedPreferences.Editor editor = preferences.edit();

        injectionProb = DEFAULT_INJECTION_PROBABILITY;
        // Make sure service won't be running next time we open the application
        editor.putBoolean("enabled", false);
        editor.commit();
    }

    class AlarmReceiver extends BroadcastReceiver {
        private long battTemperature = -1;

        @Override
        public void onReceive(Context context, Intent intent) {
            long battTemp = getBatteryTemperature();
            long cpuTemp = getCurrentTemperature();
            updateInterface(battTemp, cpuTemp);
            updateControl(battTemp, cpuTemp);
        }

        private long getBatteryTemperature() {
            Intent intent = getApplicationContext().registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            // Register application when it's installed
            if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                battTemperature = intent.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE, -1);
            }

            return battTemperature;
        }

        private void updateInterface(long battTemp, long cpuTemp) {
            Intent intent = new Intent(TemperatureControllerService.this,
                    MainActivity.class);

            intent.setAction(UPDATE_UI_ACTION);
            intent.putExtra("battTemp", battTemp);
            intent.putExtra("cpuTemp", cpuTemp);
            sendBroadcast(intent);
        }

        /*
         * Control injection probability using exponential-backoff-like
         * mechanism
         */
        private void updateControl(long battTemp, long cpuTemp) {
            int prob = injectionProb;

            if (cpuTemp >= maxTemperature) {
                prob = prob > 0 ? prob * INJECTION_INCREASE_STEP : 1;
            } else {
                prob = prob > 0 ? prob - INJECTION_DECREASE_STEP
                        : DEFAULT_INJECTION_PROBABILITY;
            }

            setInjectionProbability(prob);
        }
    }
}
