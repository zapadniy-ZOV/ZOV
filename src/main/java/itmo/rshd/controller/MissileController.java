package itmo.rshd.controller;

import itmo.rshd.model.Missile;
import itmo.rshd.model.Missile.MissileStatus;
import itmo.rshd.model.Missile.MissileType;
import itmo.rshd.service.MissileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/missiles")
public class MissileController {

    private final MissileService missileService;

    @Autowired
    public MissileController(MissileService missileService) {
        this.missileService = missileService;
    }

    @PostMapping
    public ResponseEntity<Missile> createMissile(@RequestBody Missile missile) {
        Missile createdMissile = missileService.createMissile(missile);
        return new ResponseEntity<>(createdMissile, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Missile>> getAllMissiles() {
        List<Missile> missiles = missileService.getAllMissiles();
        return new ResponseEntity<>(missiles, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Missile> getMissileById(@PathVariable String id) {
        Optional<Missile> missile = missileService.getMissileById(id);
        return missile.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Missile> updateMissile(@PathVariable String id, @RequestBody Missile missile) {
        Optional<Missile> existingMissile = missileService.getMissileById(id);
        if (existingMissile.isPresent()) {
            missile.setId(id);
            Missile updatedMissile = missileService.updateMissile(missile);
            return new ResponseEntity<>(updatedMissile, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMissile(@PathVariable String id) {
        Optional<Missile> existingMissile = missileService.getMissileById(id);
        if (existingMissile.isPresent()) {
            missileService.deleteMissile(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Missile>> getMissilesByType(@PathVariable MissileType type) {
        List<Missile> missiles = missileService.findMissilesByType(type);
        return new ResponseEntity<>(missiles, HttpStatus.OK);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Missile>> getMissilesByStatus(@PathVariable MissileStatus status) {
        List<Missile> missiles = missileService.findMissilesByStatus(status);
        return new ResponseEntity<>(missiles, HttpStatus.OK);
    }

    @GetMapping("/ready/{type}")
    public ResponseEntity<List<Missile>> getReadyMissilesByType(@PathVariable MissileType type) {
        List<Missile> missiles = missileService.findReadyMissilesByType(type);
        return new ResponseEntity<>(missiles, HttpStatus.OK);
    }

    @PutMapping("/{id}/maintenance")
    public ResponseEntity<String> performMaintenance(@PathVariable String id) {
        boolean success = missileService.performMaintenance(id);
        
        if (success) {
            return new ResponseEntity<>("Maintenance started", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Missile not found", HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/maintenance/complete")
    public ResponseEntity<String> completeMaintenance(@PathVariable String id) {
        boolean success = missileService.completeMaintenance(id);
        
        if (success) {
            return new ResponseEntity<>("Maintenance completed", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Missile not found or not in maintenance", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/{id}/deploy")
    public ResponseEntity<String> deployMissile(
            @PathVariable String id,
            @RequestParam String targetRegionId) {
        
        boolean success = missileService.deployMissile(id, targetRegionId);
        
        if (success) {
            return new ResponseEntity<>("Missile deployed successfully", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Deployment failed - check missile status or target eligibility", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/for-region/{regionId}")
    public ResponseEntity<List<Missile>> getMissilesForRegion(
            @PathVariable String regionId,
            @RequestParam double minRange) {
        
        List<Missile> missiles = missileService.findMissilesForRegion(regionId, minRange);
        return new ResponseEntity<>(missiles, HttpStatus.OK);
    }
} 