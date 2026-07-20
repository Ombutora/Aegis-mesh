package com.aegismesh.session;

import com.aegismesh.models.User;

/**
 * In-memory cache of the logged-in user's profile, populated once
 * ProfileActivity's save succeeds and read by SOSService when building the
 * emergency dispatch payload. Not persisted across process death - if the
 * app is killed and restarted, the user needs to pass through
 * login/profile again to repopulate it.
 */
public class UserSession {
    private static final UserSession INSTANCE = new UserSession();

    private User currentUser;

    private UserSession() {
    }

    public static UserSession getInstance() {
        return INSTANCE;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
}