package itmo.rshd.controller;

import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import itmo.rshd.service.MissileSupplyGraphService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/missile-supply")
public class MissileSupplyController {

    private final MissileSupplyGraphService missileSupplyGraphService;

    @Autowired
    public MissileSupplyController(MissileSupplyGraphService missileSupplyGraphService) {
        this.missileSupplyGraphService = missileSupplyGraphService;
    }

    @GetMapping("/depots")
    public ResponseEntity<List<Map<String, Object>>> getAllDepots() {
        List<Map<String, Object>> depots = missileSupplyGraphService.getAllDepots();
        return new ResponseEntity<>(depots, HttpStatus.OK);
    }
    
    @GetMapping("/depots/{depotId}")
    public ResponseEntity<Map<String, Object>> getDepotById(@PathVariable String depotId) {
        Map<String, Object> depot = missileSupplyGraphService.getDepotById(depotId);
        
        if (depot == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(depot, HttpStatus.OK);
    }
    
    @GetMapping("/routes")
    public ResponseEntity<List<Map<String, Object>>> getAllRoutes() {
        List<Map<String, Object>> routes = missileSupplyGraphService.getAllSupplyRoutes();
        return new ResponseEntity<>(routes, HttpStatus.OK);
    }
    
    @PutMapping("/routes/{sourceDepotId}/{targetDepotId}")
    public ResponseEntity<Map<String, Object>> updateRouteStatus(
            @PathVariable String sourceDepotId,
            @PathVariable String targetDepotId,
            @RequestParam boolean isActive) {
        
        Map<String, Object> updatedRoute = missileSupplyGraphService.updateRouteStatus(
            sourceDepotId, targetDepotId, isActive);
            
        if (updatedRoute == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(updatedRoute, HttpStatus.OK);
    }

    @PostMapping("/depots")
    public ResponseEntity<Map<String, Object>> addSupplyDepot(
            @RequestParam String depotId,
            @RequestParam String name,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam int capacity) {
        
        Vertex depot = missileSupplyGraphService.addSupplyDepot(depotId, name, latitude, longitude, capacity);
        
        Map<String, Object> response = Map.of(
            "depotId", depot.property("depotId").value(),
            "name", depot.property("name").value(),
            "latitude", depot.property("latitude").value(),
            "longitude", depot.property("longitude").value(),
            "capacity", depot.property("capacity").value()
        );
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/missile-types")
    public ResponseEntity<Map<String, Object>> addMissileType(
            @RequestParam String missileTypeId,
            @RequestParam String name,
            @RequestParam double range,
            @RequestParam double effectRadius) {
        
        Vertex missileType = missileSupplyGraphService.addMissileType(missileTypeId, name, range, effectRadius);
        
        Map<String, Object> response = Map.of(
            "missileTypeId", missileType.property("missileTypeId").value(),
            "name", missileType.property("name").value(),
            "range", missileType.property("range").value(),
            "effectRadius", missileType.property("effectRadius").value()
        );
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/routes")
    public ResponseEntity<Map<String, Object>> addSupplyRoute(
            @RequestParam String sourceDepotId,
            @RequestParam String targetDepotId,
            @RequestParam double distance,
            @RequestParam double riskFactor) {
        
        Edge route = missileSupplyGraphService.addSupplyRoute(sourceDepotId, targetDepotId, distance, riskFactor);
        
        Map<String, Object> response = Map.of(
            "sourceDepotId", sourceDepotId,
            "targetDepotId", targetDepotId,
            "distance", route.property("distance").value(),
            "riskFactor", route.property("riskFactor").value(),
            "isActive", route.property("isActive").value()
        );
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/depots/{depotId}/missiles")
    public ResponseEntity<String> addMissilesToDepot(
            @PathVariable String depotId,
            @RequestParam String missileTypeId,
            @RequestParam int quantity) {
        
        missileSupplyGraphService.addMissilesToDepot(depotId, missileTypeId, quantity);
        
        return new ResponseEntity<>("Missiles added to depot successfully", HttpStatus.OK);
    }

    @GetMapping("/routes/optimal")
    public ResponseEntity<List<Map<String, Object>>> findOptimalRoute(
            @RequestParam String fromDepotId,
            @RequestParam String toDepotId) {
        
        List<Map<String, Object>> route = missileSupplyGraphService.findOptimalSupplyRoute(fromDepotId, toDepotId);
        
        if (route.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(route, HttpStatus.OK);
    }

    @GetMapping("/depots/missiles")
    public ResponseEntity<List<Map<String, Object>>> findDepotsWithMissileType(
            @RequestParam String missileTypeId,
            @RequestParam(defaultValue = "1") int minQuantity) {
        
        List<Map<String, Object>> depots = missileSupplyGraphService.findDepotsWithMissileType(missileTypeId, minQuantity);
        
        return new ResponseEntity<>(depots, HttpStatus.OK);
    }
    
    @PostMapping("/admin/reset-supply-chain")
    public ResponseEntity<String> resetSupplyChain() {
        missileSupplyGraphService.clearSupplyChain();
        return new ResponseEntity<>("Supply chain has been reset. It will be regenerated on the next application restart.", HttpStatus.OK);
    }
    
    @PostMapping("/admin/generate-supply-chain")
    public ResponseEntity<String> generateSupplyChain() {
        try {
            int count = missileSupplyGraphService.generateSampleSupplyChain();
            return new ResponseEntity<>("Supply chain generated with " + count + " depots.", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error generating supply chain: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
} 