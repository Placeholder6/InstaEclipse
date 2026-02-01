package ps.reso.instaeclipse.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import ps.reso.instaeclipse.R; // Uses YOUR module's R class
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public class EclipseSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the XML layout from your module's resources
        setContentView(R.layout.activity_eclipse_settings);
        
        // Initialize settings manager to ensure flags are loaded
        SettingsManager.init(this);

        setupUI();
    }

    private void setupUI() {
        // Example: Setup the Ghost Mode switch
        Switch ghostSwitch = findViewById(R.id.switch_ghost_mode);
        if (ghostSwitch != null) {
            ghostSwitch.setChecked(FeatureFlags.isGhostModeEnabled);
            ghostSwitch.setOnCheckedChangeListener((v, isChecked) -> {
                FeatureFlags.isGhostModeEnabled = isChecked;
                SettingsManager.saveAllFlags(); // Save immediately
            });
        }
        
        // Close button logic
        View closeBtn = findViewById(R.id.btn_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> finish());
        }
    }
}
