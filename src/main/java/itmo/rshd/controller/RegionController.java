package itmo.rshd.controller;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.Region;
import itmo.rshd.model.Region.RegionType;
import itmo.rshd.model.User;
import itmo.rshd.service.RegionService;
import itmo.rshd.service.WebSocketService;
import itmo.rshd.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;
    private final WebSocketService webSocketService;
    private final UserService userService;

    @Autowired
    public RegionController(RegionService regionService, WebSocketService webSocketService, UserService userService) {
        this.regionService = regionService;
        this.webSocketService = webSocketService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Region> createRegion(@RequestBody Region region) {
        Region createdRegion = regionService.createRegion(region);
        return new ResponseEntity<>(createdRegion, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Region>> getAllRegions() {
        List<Region> regions = regionService.getAllRegions();
        return new ResponseEntity<>(regions, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Region> getRegionById(@PathVariable String id) {
        Optional<Region> region = regionService.getRegionById(id);
        return region.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Region> updateRegion(@PathVariable String id, @RequestBody Region region) {
        Optional<Region> existingRegion = regionService.getRegionById(id);
        if (existingRegion.isPresent()) {
            region.setId(id);
            Region updatedRegion = regionService.updateRegion(region);

            // Notify all subscribers about region update
            webSocketService.notifyRegionStatusUpdate(updatedRegion);

            return new ResponseEntity<>(updatedRegion, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRegion(@PathVariable String id) {
        Optional<Region> existingRegion = regionService.getRegionById(id);
        if (existingRegion.isPresent()) {
            regionService.deleteRegion(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Region>> getRegionsByType(@PathVariable RegionType type) {
        List<Region> regions = regionService.findRegionsByType(type);
        return new ResponseEntity<>(regions, HttpStatus.OK);
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<Region>> getSubRegions(@PathVariable String parentId) {
        List<Region> regions = regionService.findSubRegions(parentId);
        return new ResponseEntity<>(regions, HttpStatus.OK);
    }

    @GetMapping("/containing")
    public ResponseEntity<List<Region>> getRegionsContainingPoint(
            @RequestParam double latitude,
            @RequestParam double longitude) {

        GeoLocation location = new GeoLocation(latitude, longitude);
        List<Region> regions = regionService.findRegionsContainingPoint(location);
        return new ResponseEntity<>(regions, HttpStatus.OK);
    }

    @GetMapping("/low-rated")
    public ResponseEntity<List<Region>> getLowRatedRegionsWithoutImportantPersons(
            @RequestParam double threshold) {

        List<Region> regions = regionService.findLowRatedRegionsWithoutImportantPersons(threshold);
        return new ResponseEntity<>(regions, HttpStatus.OK);
    }

    @PutMapping("/{id}/statistics")
    public ResponseEntity<Region> updateRegionStatistics(@PathVariable String id) {
        Region updatedRegion = regionService.updateRegionStatistics(id);

        if (updatedRegion != null) {
            // Notify all subscribers about region update
            webSocketService.notifyRegionStatusUpdate(updatedRegion);

            return new ResponseEntity<>(updatedRegion, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/statistics/all")
    public ResponseEntity<Void> updateAllRegionsStatistics() {
        regionService.updateAllRegionsStatistics();

        // Get all regions and notify about their updates
        List<Region> regions = regionService.getAllRegions();
        for (Region region : regions) {
            webSocketService.notifyRegionStatusUpdate(region);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/under-threat/{type}")
    public ResponseEntity<List<Region>> getRegionsUnderThreat(@PathVariable RegionType type) {
        List<Region> regions = regionService.findRegionsUnderThreat(type);
        return new ResponseEntity<>(regions, HttpStatus.OK);
    }

    @GetMapping("/{id}/eliminated-users")
    public ResponseEntity<List<User>> getEliminatedUsersInRegion(@PathVariable String id) {
        List<User> eliminatedUsers = userService.getEliminatedUsersInRegion(id);
        if (eliminatedUsers != null) {
            return new ResponseEntity<>(eliminatedUsers, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}