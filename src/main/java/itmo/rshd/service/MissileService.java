package itmo.rshd.service;

import itmo.rshd.model.Missile;
import itmo.rshd.model.Missile.MissileStatus;
import itmo.rshd.model.Missile.MissileType;
import itmo.rshd.model.Region;
import itmo.rshd.repository.MissileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MissileService {

    private final MissileRepository missileRepository;
    private final RegionService regionService;
    @Autowired
    public MissileService(MissileRepository missileRepository, 
                          RegionService regionService,
                          MissileSupplyGraphService missileSupplyGraphService) {
        this.missileRepository = missileRepository;
        this.regionService = regionService;
    }

    public Missile createMissile(Missile missile) {
        return missileRepository.save(missile);
    }

    public List<Missile> getAllMissiles() {
        return missileRepository.findAll();
    }

    public Optional<Missile> getMissileById(String id) {
        return missileRepository.findById(id);
    }

    public Missile updateMissile(Missile missile) {
        return missileRepository.save(missile);
    }

    public void deleteMissile(String id) {
        missileRepository.deleteById(id);
    }

    public List<Missile> findMissilesByType(MissileType type) {
        return missileRepository.findByType(type);
    }

    public List<Missile> findMissilesByStatus(MissileStatus status) {
        return missileRepository.findByStatus(status);
    }

    public List<Missile> findReadyMissilesByType(MissileType type) {
        return missileRepository.findReadyMissilesByType(type);
    }

    public boolean performMaintenance(String missileId) {
        Optional<Missile> missileOpt = missileRepository.findById(missileId);
        if (missileOpt.isPresent()) {
            Missile missile = missileOpt.get();
            missile.setStatus(MissileStatus.IN_MAINTENANCE);
            missile.setLastMaintenanceDate(LocalDateTime.now());
            missileRepository.save(missile);
            return true;
        }
        return false;
    }

    public boolean deployMissile(String missileId, String targetRegionId) {
        Optional<Missile> missileOpt = missileRepository.findById(missileId);
        Optional<Region> regionOpt = regionService.getRegionById(targetRegionId);
        
        if (missileOpt.isPresent() && regionOpt.isPresent()) {
            Missile missile = missileOpt.get();
            Region targetRegion = regionOpt.get();
            
            // Check if missile is ready
            if (missile.getStatus() != MissileStatus.READY) {
                return false;
            }
            
            // Check if region is eligible for targeting (low rating, no important persons)
            if (!targetRegion.isUnderThreat()) {
                return false;
            }
            
            // Update missile status
            missile.setStatus(MissileStatus.DEPLOYED);
            missileRepository.save(missile);
            
            // In a real system, we would have additional logic for the actual "targeting" operation
            // This is just a placeholder for the missile deployment logic
            
            return true;
        }
        return false;
    }

    public List<Missile> findMissilesForRegion(String regionId, double minRange) {
        Optional<Region> regionOpt = regionService.getRegionById(regionId);
        if (regionOpt.isPresent()) {
            return missileRepository.findReadyMissilesWithRangeAtLeast(minRange);
        }
        return List.of();
    }

    public boolean completeMaintenance(String missileId) {
        Optional<Missile> missileOpt = missileRepository.findById(missileId);
        if (missileOpt.isPresent()) {
            Missile missile = missileOpt.get();
            if (missile.getStatus() == MissileStatus.IN_MAINTENANCE) {
                missile.setStatus(MissileStatus.READY);
                missileRepository.save(missile);
                return true;
            }
        }
        return false;
    }
} 