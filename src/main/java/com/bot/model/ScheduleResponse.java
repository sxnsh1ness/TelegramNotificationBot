package com.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ScheduleResponse {
    private List<Region> regions;
    @JsonProperty("date_today")
    private String dateToday;
    @JsonProperty("date_tomorrow")
    private String dateTomorrow;

    public List<Region> getRegions() { return regions; }
    public void setRegions(List<Region> regions) { this.regions = regions; }
    public String getDateToday() { return dateToday; }
    public void setDateToday(String dateToday) { this.dateToday = dateToday; }
    public String getDateTomorrow() { return dateTomorrow; }
    public void setDateTomorrow(String dateTomorrow) { this.dateTomorrow = dateTomorrow; }
}
