package com.aegismesh.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class IdVerificationActivity extends AppCompatActivity {
    public static void start(Context context) {
        context.startActivity(new Intent(context, IdVerificationActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Placeholder implementation
    }
}
