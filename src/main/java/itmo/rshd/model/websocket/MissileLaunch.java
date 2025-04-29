package itmo.rshd.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MissileLaunch {
    private String regionId;
    private String missileType;
    private String launchedById; // ID of user who launched the missile
} 