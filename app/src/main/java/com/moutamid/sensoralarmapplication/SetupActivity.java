package com.moutamid.sensoralarmapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SetupActivity extends AppCompatActivity {

    EditText editIp, editUserId;
    Button btnSave;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        editIp = findViewById(R.id.editIp);
        editUserId = findViewById(R.id.editUserId);
        btnSave = findViewById(R.id.btnSave);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        editIp.setText(prefs.getString("server_ip", ""));
        editUserId.setText(prefs.getString("user_id", ""));

        btnSave.setOnClickListener(v -> {
            String ip = editIp.getText().toString().trim();
            String userId = editUserId.getText().toString().trim();

            if (ip.isEmpty() || userId.isEmpty()) {
                Toast.makeText(this, "Both fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            int userIdInt;
            try {
                userIdInt = Integer.parseInt(userId);
                if (userIdInt < 1 || userIdInt > 15) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "User ID must be between 1 and 15", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    .putString("server_ip", ip)
                    .putString("user_id", userId)
                    .apply();

            startActivity(new Intent(SetupActivity.this, MainActivity.class));
            finish();
        });
    }
}
