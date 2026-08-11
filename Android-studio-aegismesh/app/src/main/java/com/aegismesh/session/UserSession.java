package com.aegismesh.session;

import com.aegismesh.models.User;

public class UserSession {
    private static final UserSession INSTANCE = new UserSession();
    private User currentUser;
    private UserSession() { }
    public static UserSession getInstance() { return INSTANCE; }
    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User user) { currentUser = user; }
}
