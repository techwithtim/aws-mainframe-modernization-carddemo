package com.aws.carddemo.dto;

import java.util.List;

public class MenuResponse {
    
    private String menuTitle;
    private String menuType;
    private List<MenuOption> options;
    private String userType;
    private String userName;

    public MenuResponse() {
    }

    public MenuResponse(String menuTitle, String menuType, List<MenuOption> options, String userType, String userName) {
        this.menuTitle = menuTitle;
        this.menuType = menuType;
        this.options = options;
        this.userType = userType;
        this.userName = userName;
    }

    public String getMenuTitle() {
        return menuTitle;
    }

    public void setMenuTitle(String menuTitle) {
        this.menuTitle = menuTitle;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public List<MenuOption> getOptions() {
        return options;
    }

    public void setOptions(List<MenuOption> options) {
        this.options = options;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
