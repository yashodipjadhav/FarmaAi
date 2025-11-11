package com.example.banana_desies_detection;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignupActivity extends AppCompatActivity {

    private EditText nameEdit, emailEdit, passwordEdit;
    private Button signupButton;
    private TextView signupText; // 👈 added

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        nameEdit = findViewById(R.id.name);
        emailEdit = findViewById(R.id.email);
        passwordEdit = findViewById(R.id.password);
        signupButton = findViewById(R.id.signupButton);
        signupText = findViewById(R.id.signupText); // 👈 find the TextView

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");

        signupButton.setOnClickListener(v -> registerUser());

        // 👇 When user clicks "Already have an account? Sign In"
        signupText.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, SigninActivity.class);
            startActivity(intent);
            finish(); // optional, so user can’t come back here after signing in
        });
    }

    private void registerUser() {
        String name = nameEdit.getText().toString().trim();
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            nameEdit.setError("Enter your name");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailEdit.setError("Enter email");
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            passwordEdit.setError("Password must be 6+ characters");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        User user = new User(name, email, password);
                        databaseReference.child(userId).setValue(user);

                        Toast.makeText(SignupActivity.this, "Signup Successful", Toast.LENGTH_SHORT).show();

                        // redirect to SigninActivity
                        startActivity(new Intent(SignupActivity.this, SigninActivity.class));
                        finish();
                    } else {
                        Toast.makeText(SignupActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // User model
    public static class User {
        public String name, email, password;

        public User() {} // Firebase needs empty constructor

        public User(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }
}
