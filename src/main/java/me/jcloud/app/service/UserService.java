package me.jcloud.app.service;

import me.jcloud.app.model.User;

public interface UserService {
    User registerUser(String username, String email, String password);
    User authenticate(String email, String password);
}
