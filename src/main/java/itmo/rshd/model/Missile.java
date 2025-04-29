package itmo.rshd.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "missiles")
public class Missile {
    @Id
    private String id;
    private String name;
    private MissileType type;
    private MissileStatus status;
    private double range; // in kilometers
    private double effectRadius; // area of effect in kilometers
    private LocalDateTime lastMaintenanceDate;
    private String supplyDepotId; // Reference to where this missile is stored
    private GeoLocation currentLocation;
    
    public enum MissileType {
        ORESHNIK,
        KINZHAL,
        SARMAT
    }
    
    public enum MissileStatus {
        READY,
        IN_MAINTENANCE,
        DEPLOYED,
        DECOMMISSIONED
    }
} 