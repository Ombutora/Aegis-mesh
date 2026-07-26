package com.aegismesh.network;

import com.aegismesh.models.MedicalProfile;
import com.aegismesh.models.User;
import com.aegismesh.models.VerificationLevel;

public class UserService {
    private User cachedUser;
    public void getCurrentUser(ApiCallback<User> callback) {
        new Thread(() -> {
            if (cachedUser == null) {
                cachedUser = new User("", "", "", "");
                cachedUser.setMedicalProfile(new MedicalProfile());
                cachedUser.setVerificationLevel(new VerificationLevel(false, false));
            }
            callback.onSuccess(cachedUser);
        }).start();
    }
    public void updateProfile(String fullName, MedicalProfile profile, ApiCallback<User> callback) {
        new Thread(() -> {
            if (cachedUser == null) {
                cachedUser = new User(fullName, "", "", "");
                cachedUser.setVerificationLevel(new VerificationLevel(false, false));
            } else {
                cachedUser.setFullName(fullName);
            }
            cachedUser.setMedicalProfile(profile);
            callback.onSuccess(cachedUser);
        }).start();
    }
}
