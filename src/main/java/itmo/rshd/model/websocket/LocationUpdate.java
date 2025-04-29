package itmo.rshd.model.websocket;

import itmo.rshd.model.GeoLocation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LocationUpdate {
    private String userId;
    private GeoLocation location;
} 