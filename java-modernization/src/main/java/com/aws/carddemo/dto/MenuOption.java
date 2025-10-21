package com.aws.carddemo.dto;

public class MenuOption {
    
    private String code;
    private String description;
    private String action;
    private boolean enabled;

    public MenuOption() {
    }

    public MenuOption(String code, String description, String action, boolean enabled) {
        this.code = code;
        this.description = description;
        this.action = action;
        this.enabled = enabled;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
