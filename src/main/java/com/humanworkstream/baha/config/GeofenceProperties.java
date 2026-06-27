package com.humanworkstream.baha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bounding box that accepted flood-report coordinates must fall inside (Metro Manila + Rizal, with
 * a small margin). Bound from {@code baha.geofence.*}; set {@code enabled=false} to turn off.
 */
@Component
@ConfigurationProperties(prefix = "baha.geofence")
@Data
public class GeofenceProperties {
    private boolean enabled = true;
    private double minLat = 14.2;
    private double maxLat = 15.0;
    private double minLng = 120.8;
    private double maxLng = 121.5;

    public boolean contains(double lat, double lng) {
        return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
    }
}
