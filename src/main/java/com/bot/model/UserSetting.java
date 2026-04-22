package com.bot.model;

public class UserSetting {
    private long userId;
    private String region;
    private String queue;
    private String mode = "normal";
    private int notifyBefore = 5;
    private int notifyReturnBefore = 0;
    private boolean notifyOutage = true;
    private boolean notifyReturn = true;
    private boolean notifyChanges = true;
    private String displayMode = "blackout";
    private boolean isActive = true;

    // Getters and Setters
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getQueue() { return queue; }
    public void setQueue(String queue) { this.queue = queue; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public int getNotifyBefore() { return notifyBefore; }
    public void setNotifyBefore(int notifyBefore) { this.notifyBefore = notifyBefore; }
    public int getNotifyReturnBefore() { return notifyReturnBefore; }
    public void setNotifyReturnBefore(int notifyReturnBefore) { this.notifyReturnBefore = notifyReturnBefore; }
    public boolean isNotifyOutage() { return notifyOutage; }
    public void setNotifyOutage(boolean notifyOutage) { this.notifyOutage = notifyOutage; }
    public boolean isNotifyReturn() { return notifyReturn; }
    public void setNotifyReturn(boolean notifyReturn) { this.notifyReturn = notifyReturn; }
    public boolean isNotifyChanges() { return notifyChanges; }
    public void setNotifyChanges(boolean notifyChanges) { this.notifyChanges = notifyChanges; }
    public String getDisplayMode() { return displayMode; }
    public void setDisplayMode(String displayMode) { this.displayMode = displayMode; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
