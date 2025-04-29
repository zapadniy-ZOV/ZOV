package itmo.rshd.repository;

import itmo.rshd.model.Missile;
import itmo.rshd.model.Missile.MissileStatus;
import itmo.rshd.model.Missile.MissileType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MissileRepository extends MongoRepository<Missile, String> {
    
    List<Missile> findByType(MissileType type);
    
    List<Missile> findByStatus(MissileStatus status);
    
    List<Missile> findBySupplyDepotId(String supplyDepotId);
    
    @Query("{'type': ?0, 'status': 'READY'}")
    List<Missile> findReadyMissilesByType(MissileType type);
    
    @Query("{'range': {$gte: ?0}, 'status': 'READY'}")
    List<Missile> findReadyMissilesWithRangeAtLeast(double minRange);
    
    @Query("{'currentLocation': {$near: {$geometry: {type: 'Point', coordinates: [?0, ?1]}, $maxDistance: ?2}}}")
    List<Missile> findMissilesNearLocation(double longitude, double latitude, double maxDistanceMeters);
} 