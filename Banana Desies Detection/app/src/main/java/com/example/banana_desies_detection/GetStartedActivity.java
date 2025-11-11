package com.example.banana_desies_detection;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class GetStartedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getstarted);

        // Find the button from layout
        Button getStartedButton = findViewById(R.id.getStartedButton);

        // Set button click listener to go to LoginActivity (SigninActivity)
        getStartedButton.setOnClickListener(v -> {
            Intent intent = new Intent(GetStartedActivity.this, SigninActivity.class);
            startActivity(intent);
            finish(); // Optional: close Intro screen
        });
    }
}
