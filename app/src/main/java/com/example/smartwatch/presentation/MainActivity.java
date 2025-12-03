package com.example.smartwatch.presentation;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartdoorwatch.R;

public class MainActivity extends AppCompatActivity {

    private LinearLayout doorSelectorLayout;
    private LinearLayout doorDetailLayout;

    private Button unlockButton;

    private TextView doorNameText;
    private TextView doorStatusText;

    private String selectedDoor = "Front Door";

    // Door State
    private boolean isFrontDoorLocked = true;

    // SharedPreferences
    private SharedPreferences prefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize SharedPreferences
        prefs = getSharedPreferences("door_state", MODE_PRIVATE);

        // Load saved values
        isFrontDoorLocked = prefs.getBoolean("front_locked", true);

        // Bind Views
        doorSelectorLayout = findViewById(R.id.doorSelectorLayout);
        doorDetailLayout   = findViewById(R.id.doorDetailLayout);

        Button frontDoorButton = findViewById(R.id.frontDoorButton);
        unlockButton    = findViewById(R.id.unlockButton);
        Button backButton = findViewById(R.id.backButton);

        doorNameText   = findViewById(R.id.doorNameText);
        doorStatusText = findViewById(R.id.doorStatusText);
        TextView errorText = findViewById(R.id.errorText);

        // Door Selection Buttons
        frontDoorButton.setOnClickListener(v -> {
            selectedDoor = "Front Door";
            showDoorDetailScreen();
        });

        // Unlock Button
        unlockButton.setOnClickListener(v -> {

            // This is placeholder logic until backend replaces it.
            if (selectedDoor.equals("Front Door")) {
                isFrontDoorLocked = !isFrontDoorLocked;
            }

            saveDoorState();
            updateDoorDetailUI();
        });

        // Back Button
        backButton.setOnClickListener(v -> {
            doorDetailLayout.setVisibility(View.GONE);
            doorSelectorLayout.setVisibility(View.VISIBLE);
        });
    }


    private void showDoorDetailScreen() {
        doorSelectorLayout.setVisibility(View.GONE);
        doorDetailLayout.setVisibility(View.VISIBLE);
        updateDoorDetailUI();
    }

    private void updateDoorDetailUI() {
        doorNameText.setText(selectedDoor);

        boolean locked = true;
        if (selectedDoor.equals("Front Door")){
            locked = isFrontDoorLocked;
        }

        if (locked) {
            doorStatusText.setText("Door Status: Locked");
            unlockButton.setText("UNLOCK");
        } else {
            doorStatusText.setText("Door Status: Unlocked");
            unlockButton.setText("LOCK");
        }
    }

    private void saveDoorState() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("front_locked", isFrontDoorLocked);
        editor.apply();
    }
}

