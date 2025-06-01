package com.moutamid.sensoralarmapplication;

public class SensorEvent {
    public String sensorId;
    public int position;
    public int status;
    public String description;

    public SensorEvent(int sensorId, int position, int status, String description) {
        this.sensorId = String.valueOf(sensorId);
        this.position = position;
        this.status = status;
        this.description = description;
    }
}

