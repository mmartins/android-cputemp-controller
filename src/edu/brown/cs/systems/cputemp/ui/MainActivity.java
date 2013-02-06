package edu.brown.cs.systems.cputemp.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.Toast;
import edu.brown.cs.systems.cputemp.R;
import edu.brown.cs.systems.cputemp.services.TemperatureControllerService;

public class MainActivity extends Activity {

    private Switch tempControllerSwitch;
    private NumberPicker temperaturePicker;
    private Button applyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tempControllerSwitch = (Switch) findViewById(R.id.injectionSwitch);
        temperaturePicker = (NumberPicker) findViewById(R.id.temperaturePicker);
        applyButton = (Button) findViewById(R.id.applyButton);

        String[] nums = new String[100];
        for (int i = 1; i <= nums.length; i++)
            nums[i - 1] = Integer.toString(i);

        applyButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (!tempControllerSwitch.isEnabled()) {
                    Toast.makeText(MainActivity.this,
                            "Controller is not enabled", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Intent intent = new Intent(MainActivity.this,
                            TemperatureControllerService.class);
                    intent.putExtra("enabled", tempControllerSwitch.isEnabled());
                    intent.putExtra("maxTemp",
                            Integer.toString(temperaturePicker.getValue()));

                    startService(intent);
                }
            }
        });
    }

    /* Save interface state when screen gets out of focus */
    @Override
    public void onPause() {
        super.onPause();

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("controllerOn", tempControllerSwitch.isChecked());
        editor.putInt("temperature", temperaturePicker.getValue());

        editor.commit();
    }

    /* Recover interface state when screen comes back to focus */
    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(MainActivity.this);

        tempControllerSwitch.setEnabled(preferences
                .getBoolean("controllerOn", false));
        temperaturePicker.setEnabled(preferences.getBoolean("controllerOn",
                false));
        temperaturePicker.setValue(preferences.getInt("temperature",
                TemperatureControllerService.DEFAULT_TEMPERATURE));
    }
}
