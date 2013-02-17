package edu.brown.cs.systems.cputemp.utils;

import java.io.IOException;

import android.util.Log;

import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.Shell;

public class KernelParameterIO {
    static private final String TAG = "KernelParameterIO";

    private Shell shell;
    private String cmdReturn;
    private static KernelParameterIO instance;

    private KernelParameterIO() {
        try {
            shell = RootTools.getShell(true);
        } catch (Exception e) {
            Log.e(TAG, "Can't access shell. Service stopped.");
            e.printStackTrace();
        }
    }

    public static KernelParameterIO getInstance() {
        if (instance == null) {
            instance = new KernelParameterIO();
            // If shell can't be started, instance is not completed
            if (instance.shell == null)
                instance = null;
        }

        return instance;
    }

    public void release() {
        if (shell != null) {
            try {
                shell.close();
            } catch (IOException e) {
                Log.e(TAG, "Can't close shell");
                e.printStackTrace();
            }
        }

        instance = null;
    }

    public String readSysFs(String path) {
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
                    return null;
                }
            } else {
                Log.e(TAG, "Controller requires root access to work");
                return null;
            }
        } else {
            Log.e(TAG, "Controller only works on rooted devices");
        }

        return cmdReturn;
    }

    public boolean writeSysFs(String path, String value) {
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
                    e.printStackTrace();
                }
            } else {
                Log.e(TAG, "Controller requires root access to work");
            }
        } else {
            Log.e(TAG, "Controller only works on rooted devices");
        }

        return (cmdReturn.equals("0") ? true : false);
    }
}
