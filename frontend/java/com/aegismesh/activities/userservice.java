package com.aegismesh.network;

import com.aegismesh.models.MedicalProfile;
import com.aegismesh.models.User;
import com.aegismesh.models.VerificationLevel;

/**
 * MOCK IMPLEMENTATION. Per the project audit, the backend initializes a
 * users table but no route currently reads or writes it. This echoes data
 * in-memory (per process lifetime) so ProfileActivity is usable end-to-end
 * during development. Replace with real HTTP calls once backend
 * user-profile endpoints exist.
 */
public class UserService {

    private User cachedUser;

    public void getCurrentUser(ApiCallback<User> callback) {
        new Thread(() -> {
            try {
                Thread.sleep(600);
                if (cachedUser == null) {
                    cachedUser = new User("", "", "", "");
                    cachedUser.setMedicalProfile(new MedicalProfile());
                    cachedUser.setVerificationLevel(new VerificationLevel(false, false));
                }
                callback.onSuccess(cachedUser);
            } catch (InterruptedException e) {
                callback.onError(e);
            }
        }).start();
    }

    public void updateProfile(String fullName, MedicalProfile profile, ApiCallback<User> callback) {
        new Thread(() -> {
            try {
                Thread.sleep(600);
                if (cachedUser == null) {
                    cachedUser = new User(fullName, "", "", "");
                    cachedUser.setVerificationLevel(new VerificationLevel(false, false));
                } else {
                    cachedUser.setFullName(fullName);
                }
                cachedUser.setMedicalProfile(profile);
                callback.onSuccess(cachedUser);
            } catch (InterruptedException e) {
                callback.onError(e);
            }
        }).start();
    }
}