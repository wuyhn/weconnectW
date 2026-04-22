package com.example.weconnect.models;

public class User {
    private Long id;
    private String email;
    private String password;
    private String fullName;
    private String birthday;
    private String gender;

    // Constructor trống (Bắt buộc phải có để Gson hoạt động)
    public User() {}

    // Getter và Setter cho tất cả các trường
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}
