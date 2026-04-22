package com.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class Region {
    @JsonProperty("name_ua")
    private String nameUa;
    private Map<String, Map<String, Object>> schedule;
    private boolean emergency;

    public String getNameUa() { return nameUa; }
    public void setNameUa(String nameUa) { this.nameUa = nameUa; }
    public Map<String, Map<String, Object>> getSchedule() { return schedule; }
    public void setSchedule(Map<String, Map<String, Object>> schedule) { this.schedule = schedule; }
    public boolean isEmergency() { return emergency; }
    public void setEmergency(boolean emergency) { this.emergency = emergency; }
}
