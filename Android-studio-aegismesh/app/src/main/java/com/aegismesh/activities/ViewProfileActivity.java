package com.aegismesh.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.aegismesh.R;
import com.aegismesh.models.MedicalProfile;
import com.aegismesh.models.User;
import com.aegismesh.models.VerificationLevel;

public class ViewProfileActivity extends AppCompatActivity {

    private TextView textFullName, textBloodGroup, textAllergies, textConditions, textMedications, textVerificationBadge;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_profile);

        initViews();
        loadProfileData();

        findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });
        
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
    }

    private void initViews() {
        textFullName = findViewById(R.id.textFullName);
        textBloodGroup = findViewById(R.id.textBloodGroup);
        textAllergies = findViewById(R.id.textAllergies);
        textConditions = findViewById(R.id.textConditions);
        textMedications = findViewById(R.id.textMedications);
        textVerificationBadge = findViewById(R.id.textVerificationBadge);
    }

    private void loadProfileData() {
        User user = ProfileActivity.getSavedUser(this);
        if (user != null) {
            textFullName.setText(user.getFullName());
            
            MedicalProfile mp = user.getMedicalProfile();
            if (mp != null) {
                textBloodGroup.setText(mp.getBloodGroup().isEmpty() ? "Not specified" : mp.getBloodGroup());
                textAllergies.setText(mp.allergiesCsv().isEmpty() ? "None" : mp.allergiesCsv());
                textConditions.setText(mp.chronicIllnessesCsv().isEmpty() ? "None" : mp.chronicIllnessesCsv());
                textMedications.setText(mp.currentMedicationsCsv().isEmpty() ? "None" : mp.currentMedicationsCsv());
            }

            VerificationLevel vl = user.getVerificationLevel();
            if (vl != null && vl.hasNationalId() && vl.hasFaceMatch()) {
                textVerificationBadge.setVisibility(View.VISIBLE);
                textVerificationBadge.setText("Fully Verified");
            } else {
                textVerificationBadge.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileData(); // Refresh if updated in ProfileActivity
    }
}
