package itmo.rshd.model;

import lombok.Data;

@Data
public class Location {
    private Double latitude;
    private Double longitude;
    private String region;
    private Long lastUpdated;
}
