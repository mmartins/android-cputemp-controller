package edu.brown.cs.systems.cputemp.services;

import java.io.IOException;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.Shell;

public class TemperatureControllerService extends Service {

    private static final String TAG = "TemperatureControllerService";
    private boolean enabled;
    private int maxTemperature;

    private static final String SYSFS_ENABLE_PATH = "/sys/....";
    private static final String SYSFS_TEMPERATURE_PATH = "/sys/....";
    public static final int DEFAULT_TEMPERATURE = 60;

    private Shell shell;
    private String cmdReturn;

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
        enabled = intent.getBooleanExtra("enabled", false);
        maxTemperature = intent.getIntExtra("maxTemp", DEFAULT_TEMPERATURE);

        switchIdleInjection(enabled);

        if (enabled)
            setMaxTemperature(maxTemperature);

        return START_NOT_STICKY;
    }

    public boolean isIdleInjecting() {
        String ans = readSysFs(SYSFS_ENABLE_PATH);
        return ((ans != null && ans.equals("1")) ? true : false);
    }

    public boolean switchIdleInjection(boolean onOff) {
        boolean ret = false;
        if (ret = writeSysFs(SYSFS_ENABLE_PATH, onOff ? "1" : "0"))
            enabled = onOff;
        return ret;
    }

    public int getMaxTemperature() {
        String ans = readSysFs(SYSFS_TEMPERATURE_PATH);
        return (ans != null ? Integer.parseInt(ans) : -1);
    }

    public void setMaxTemperature(int maxTemperature) {
        assert maxTemperature > 0 : maxTemperature;
        if (writeSysFs(SYSFS_TEMPERATURE_PATH, Integer.toString(maxTemperature))) {
            this.maxTemperature = maxTemperature;
        } else {
            Toast.makeText(TemperatureControllerService.this,
                    "Can't set max temperature", Toast.LENGTH_SHORT).show();
        }
    }

    String readSysFs(String path) {

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

    boolean writeSysFs(String path, String value) {

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
}
