package com.aegismesh.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SelfieVerificationActivity extends AppCompatActivity {
    public static void start(Context context) {
        context.startActivity(new Intent(context, SelfieVerificationActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Placeholder implementation
    }
}
