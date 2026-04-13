package com.joinlivora.backend.auth.dto;

public class LoginApiResponse {
    private String token;
    private String refreshToken;
    private UserDto user;
    private boolean requiresTwoFactor;
    private boolean requiresTwoFactorSetup;
    private String preAuthToken;

    public LoginApiResponse() {}

    public LoginApiResponse(String token, String refreshToken, UserDto user) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public static LoginApiResponse twoFactorRequired(String preAuthToken) {
        LoginApiResponse r = new LoginApiResponse();
        r.requiresTwoFactor = true;
        r.preAuthToken = preAuthToken;
        return r;
    }

    public static LoginApiResponse twoFactorSetupRequired(String preAuthToken) {
        LoginApiResponse r = new LoginApiResponse();
        r.requiresTwoFactorSetup = true;
        r.preAuthToken = preAuthToken;
        return r;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }

    public boolean isRequiresTwoFactor() {
        return requiresTwoFactor;
    }

    public boolean isRequiresTwoFactorSetup() {
        return requiresTwoFactorSetup;
    }

    public String getPreAuthToken() {
        return preAuthToken;
    }
}
