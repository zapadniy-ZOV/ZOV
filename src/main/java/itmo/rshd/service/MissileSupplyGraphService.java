package itmo.rshd.service;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MissileSupplyGraphService {

    private final GraphTraversalSource g;

    @Autowired
    public MissileSupplyGraphService(GraphTraversalSource g) {
        this.g = g;
    }

    @PostConstruct
    public void initialize() {
        // Create schema if needed
        if (g.V().hasLabel("SupplyDepot").count().next() == 0) {
            createInitialSchema();
        }
    }

    private void createInitialSchema() {
        // Create property keys and indices
        // No transaction needed for remote graph
    }

    public Vertex addSupplyDepot(String depotId, String name, double latitude, double longitude, int capacity) {
        Vertex depot = g.addV("SupplyDepot")
                .property("depotId", depotId)
                .property("name", name)
                .property("latitude", latitude)
                .property("longitude", longitude)
                .property("capacity", capacity)
                .property("currentStock", 0)
                .next();
        return depot;
    }

    public Vertex addMissileType(String missileTypeId, String name, double range, double effectRadius) {
        Vertex missileType = g.addV("MissileType")
                .property("missileTypeId", missileTypeId)
                .property("name", name)
                .property("range", range)
                .property("effectRadius", effectRadius)
                .next();
        return missileType;
    }

    public Edge addSupplyRoute(String sourceDepotId, String targetDepotId, double distance, double riskFactor) {
        Vertex sourceDepot = g.V().has("SupplyDepot", "depotId", sourceDepotId).next();
        Vertex targetDepot = g.V().has("SupplyDepot", "depotId", targetDepotId).next();
        
        Edge route = g.addE("SupplyRoute")
                .from(sourceDepot)
                .to(targetDepot)
                .property("distance", distance)
                .property("riskFactor", riskFactor)
                .property("isActive", true)
                .next();
        return route;
    }

    public void addMissilesToDepot(String depotId, String missileTypeId, int quantity) {
        Vertex depot = g.V().has("SupplyDepot", "depotId", depotId).next();
        Vertex missileType = g.V().has("MissileType", "missileTypeId", missileTypeId).next();
        
        // Check if there's already a supply relationship
        if (g.V(depot).outE("Supplies").inV().is(missileType).hasNext()) {
            // Update existing relationship
            Object edgeObj = g.V(depot).outE("Supplies").as("e")
                            .inV().is(missileType)
                            .select("e")
                            .next();
                            
            if (edgeObj instanceof Edge) {
                Edge supply = (Edge) edgeObj;
                Object quantityObj = supply.value("quantity");
                int currentQuantity = quantityObj instanceof Number ? ((Number) quantityObj).intValue() : 0;
                supply.property("quantity", currentQuantity + quantity);
            }
        } else {
            // Create new relationship
            g.addE("Supplies")
                .from(depot)
                .to(missileType)
                .property("quantity", quantity)
                .iterate();
        }
        
        // Update current stock
        Object stockObj = g.V(depot).values("currentStock").next();
        int currentStock = stockObj instanceof Number ? ((Number) stockObj).intValue() : 0;
        g.V(depot).property("currentStock", currentStock + quantity).iterate();
    }

    public List<Map<String, Object>> findOptimalSupplyRoute(String fromDepotId, String toDepotId) {
        try {
            // Find shortest path with lowest risk between depots
            List<Object> path = g.V().has("SupplyDepot", "depotId", fromDepotId)
                    .repeat(
                        __.outE("SupplyRoute").has("isActive", true)
                        .order().by("riskFactor", org.apache.tinkerpop.gremlin.process.traversal.Order.asc)
                        .inV().simplePath()
                    ).until(
                        __.has("SupplyDepot", "depotId", toDepotId)
                    ).path().limit(1)
                    .next()
                    .objects();
            
            return path.stream()
                    .map(object -> {
                        Map<String, Object> result = new HashMap<>();
                        if (object instanceof Vertex) {
                            Vertex v = (Vertex) object;
                            result.put("type", "depot");
                            result.put("id", v.value("depotId"));
                            result.put("name", v.value("name"));
                        } else if (object instanceof Edge) {
                            Edge e = (Edge) object;
                            result.put("type", "route");
                            result.put("distance", e.value("distance"));
                            result.put("riskFactor", e.value("riskFactor"));
                        }
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> findDepotsWithMissileType(String missileTypeId, int minQuantity) {
        List<Map<Object, Object>> results = g.V()
                .has("MissileType", "missileTypeId", missileTypeId)
                .inE("Supplies")
                .has("quantity", org.apache.tinkerpop.gremlin.process.traversal.P.gte(minQuantity))
                .outV()
                .valueMap("depotId", "name", "latitude", "longitude", "currentStock")
                .toList();
        
        return results.stream()
                .map(m -> {
                    Map<String, Object> convertedMap = new HashMap<>();
                    m.forEach((k, v) -> convertedMap.put(k.toString(), v));
                    return convertedMap;
                })
                .collect(Collectors.toList());
    }
    
    public List<Map<String, Object>> getAllDepots() {
        List<Map<Object, Object>> results = g.V()
                .hasLabel("SupplyDepot")
                .valueMap("depotId", "name", "latitude", "longitude", "capacity", "currentStock", "type", "securityLevel")
                .toList();
        
        return results.stream()
                .map(m -> {
                    Map<String, Object> convertedMap = new HashMap<>();
                    m.forEach((k, v) -> {
                        if (v instanceof List && ((List<?>) v).size() == 1) {
                            convertedMap.put(k.toString(), ((List<?>) v).get(0));
                        } else {
                            convertedMap.put(k.toString(), v);
                        }
                    });
                    return convertedMap;
                })
                .collect(Collectors.toList());
    }
    
    public Map<String, Object> getDepotById(String depotId) {
        try {
            Map<Object, Object> result = g.V()
                    .has("SupplyDepot", "depotId", depotId)
                    .valueMap("depotId", "name", "latitude", "longitude", "capacity", "currentStock", "type", "securityLevel")
                    .next();
            
            Map<String, Object> convertedMap = new HashMap<>();
            result.forEach((k, v) -> {
                if (v instanceof List && ((List<?>) v).size() == 1) {
                    convertedMap.put(k.toString(), ((List<?>) v).get(0));
                } else {
                    convertedMap.put(k.toString(), v);
                }
            });
            
            return convertedMap;
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Map<String, Object>> getAllSupplyRoutes() {
        try {
            System.out.println("Getting all supply routes...");
            
            // Try to count edges first
            long edgeCount = g.E().hasLabel("SupplyRoute").count().next();
            System.out.println("Supply route edge count: " + edgeCount);
            
            // Use a different approach: get edges with their connected vertices
            List<Map<String, Object>> routes = new ArrayList<>();
            
            // Get all depots first
            Map<Object, Vertex> depotMap = new HashMap<>();
            List<Vertex> allDepots = g.V().hasLabel("SupplyDepot").toList();
            for (Vertex depot : allDepots) {
                try {
                    Object depotId = depot.value("depotId");
                    depotMap.put(depotId, depot);
                    System.out.println("Added depot to map: " + depotId + " - " + depot.id());
                } catch (Exception e) {
                    System.err.println("Error getting depot ID: " + e.getMessage());
                }
            }
            
            // Create routes directly from pairs of depots
            for (Vertex source : allDepots) {
                for (Vertex target : allDepots) {
                    if (source != target) {
                        try {
                            // Check if an edge exists between these depots
                            List<Edge> connectingEdges = g.V(source).outE("SupplyRoute").where(__.inV().is(target)).toList();
                            
                            if (!connectingEdges.isEmpty()) {
                                Edge edge = connectingEdges.get(0);
                                System.out.println("Found edge: " + edge.id() + " from " + source.value("name") + " to " + target.value("name"));
                                
                                // Create route map
                                Map<String, Object> routeMap = new HashMap<>();
                                routeMap.put("sourceDepotId", source.value("depotId"));
                                routeMap.put("targetDepotId", target.value("depotId"));
                                
                                // Get edge properties with safe access
                                double distance = getEdgePropertySafe(edge, "distance", 1000.0);
                                double riskFactor = getEdgePropertySafe(edge, "riskFactor", 0.5);
                                boolean isActive = getEdgePropertySafe(edge, "isActive", true);
                                
                                routeMap.put("distance", distance);
                                routeMap.put("riskFactor", riskFactor);
                                routeMap.put("isActive", isActive);
                                
                                // Optional properties
                                String transportType = getEdgePropertySafe(edge, "transportType", null);
                                if (transportType != null) {
                                    routeMap.put("transportType", transportType);
                                }
                                
                                String securityLevel = getEdgePropertySafe(edge, "securityLevel", null);
                                if (securityLevel != null) {
                                    routeMap.put("securityLevel", securityLevel);
                                }
                                
                                Integer capacity = getEdgePropertySafe(edge, "capacity", null);
                                if (capacity != null) {
                                    routeMap.put("capacity", capacity);
                                }
                                
                                routes.add(routeMap);
                            }
                        } catch (Exception e) {
                            System.err.println("Error checking edge between depots: " + e.getMessage());
                        }
                    }
                }
            }
            
            System.out.println("Returning " + routes.size() + " routes");
            return routes;
            
        } catch (Exception e) {
            System.err.println("Error getting supply routes: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    // Helper method to safely get edge properties with default values
    @SuppressWarnings("unchecked")
    private <T> T getEdgePropertySafe(Edge edge, String key, T defaultValue) {
        try {
            if (edge.properties(key).hasNext()) {
                Object value = edge.value(key);
                if (defaultValue != null && value.getClass().isAssignableFrom(defaultValue.getClass())) {
                    return (T) value;
                }
                return (T) value;
            }
        } catch (Exception e) {
            System.out.println("Error getting property " + key + ": " + e.getMessage());
        }
        return defaultValue;
    }
    
    public Map<String, Object> updateRouteStatus(String sourceDepotId, String targetDepotId, boolean isActive) {
        try {
            // Find vertices by depotId
            List<Vertex> sourceVertices = g.V().has("SupplyDepot", "depotId", sourceDepotId).toList();
            List<Vertex> targetVertices = g.V().has("SupplyDepot", "depotId", targetDepotId).toList();
            
            if (sourceVertices.isEmpty() || targetVertices.isEmpty()) {
                return null;
            }
            
            Vertex sourceDepot = sourceVertices.get(0);
            Vertex targetDepot = targetVertices.get(0);
            
            // Find the edge between them
            List<Edge> edges = g.V(sourceDepot).outE("SupplyRoute").where(__.inV().is(targetDepot)).toList();
            
            if (edges.isEmpty()) {
                return null;
            }
            
            Edge route = edges.get(0);
            g.E(route.id()).property("isActive", isActive).iterate();
            
            Map<String, Object> routeMap = new HashMap<>();
            routeMap.put("sourceDepotId", sourceDepotId);
            routeMap.put("targetDepotId", targetDepotId);
            routeMap.put("distance", route.value("distance"));
            routeMap.put("riskFactor", route.value("riskFactor"));
            routeMap.put("isActive", isActive);
            
            if (route.properties("transportType").hasNext()) {
                routeMap.put("transportType", route.value("transportType"));
            }
            
            if (route.properties("securityLevel").hasNext()) {
                routeMap.put("securityLevel", route.value("securityLevel"));
            }
            
            if (route.properties("capacity").hasNext()) {
                routeMap.put("capacity", route.value("capacity"));
            }
            
            return routeMap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Clears all supply chain data from the graph database
     */
    public void clearSupplyChain() {
        // First clear all edges
        g.E().hasLabel("SupplyRoute").drop().iterate();
        g.E().hasLabel("supplyRoute").drop().iterate(); // Also clear the old label format
        g.E().hasLabel("Supplies").drop().iterate();
        
        // Then clear all vertices
        g.V().hasLabel("SupplyDepot").drop().iterate();
        g.V().hasLabel("MissileType").drop().iterate();
        
        System.out.println("Supply chain data has been cleared successfully");
    }
    
    /**
     * Generates a sample supply chain for demonstration purposes
     * This doesn't rely on regions from the database
     * @return number of depots created
     */
    public int generateSampleSupplyChain() {
        try {
            System.out.println("Generating sample supply chain...");
            
            // First clear any existing data
            clearSupplyChain();
            
            // Create hub depots
            Vertex moscowHub = g.addV("SupplyDepot")
                .property("depotId", "HUB-001")
                .property("name", "Moscow Hub")
                .property("type", "REGIONAL_HUB")
                .property("latitude", 55.7558)
                .property("longitude", 37.6173)
                .property("capacity", 10000)
                .property("currentStock", 8500)
                .property("securityLevel", "HIGH")
                .next();
            
            Vertex spbHub = g.addV("SupplyDepot")
                .property("depotId", "HUB-002")
                .property("name", "St. Petersburg Hub")
                .property("type", "REGIONAL_HUB")
                .property("latitude", 59.9343)
                .property("longitude", 30.3351)
                .property("capacity", 8000)
                .property("currentStock", 7300)
                .property("securityLevel", "HIGH")
                .next();
            
            Vertex novosibirskHub = g.addV("SupplyDepot")
                .property("depotId", "HUB-003")
                .property("name", "Novosibirsk Hub")
                .property("type", "REGIONAL_HUB")
                .property("latitude", 55.0084)
                .property("longitude", 82.9357)
                .property("capacity", 9000)
                .property("currentStock", 7040)
                .property("securityLevel", "HIGH")
                .next();
            
            // Create city depots
            Vertex kazan = g.addV("SupplyDepot")
                .property("depotId", "DEPOT-001")
                .property("name", "Kazan City Depot")
                .property("type", "CITY_DEPOT")
                .property("latitude", 55.7887)
                .property("longitude", 49.1221)
                .property("capacity", 3000)
                .property("currentStock", 2000)
                .property("securityLevel", "MEDIUM")
                .next();
            
            Vertex samara = g.addV("SupplyDepot")
                .property("depotId", "DEPOT-002")
                .property("name", "Samara City Depot")
                .property("type", "CITY_DEPOT")
                .property("latitude", 53.1950)
                .property("longitude", 50.1069)
                .property("capacity", 2500)
                .property("currentStock", 1800)
                .property("securityLevel", "MEDIUM")
                .next();
            
            Vertex vladivostok = g.addV("SupplyDepot")
                .property("depotId", "DEPOT-003")
                .property("name", "Vladivostok City Depot")
                .property("type", "CITY_DEPOT")
                .property("latitude", 43.1198)
                .property("longitude", 131.8869)
                .property("capacity", 2800)
                .property("currentStock", 2100)
                .property("securityLevel", "MEDIUM")
                .next();
            
            // Create distribution points
            Vertex podolsk = g.addV("SupplyDepot")
                .property("depotId", "DIST-001")
                .property("name", "Podolsk Distribution Point")
                .property("type", "DISTRIBUTION_POINT")
                .property("latitude", 55.4312)
                .property("longitude", 37.5447)
                .property("capacity", 600)
                .property("currentStock", 400)
                .property("securityLevel", "STANDARD")
                .next();
            
            Vertex pushkin = g.addV("SupplyDepot")
                .property("depotId", "DIST-002")
                .property("name", "Pushkin Distribution Point")
                .property("type", "DISTRIBUTION_POINT")
                .property("latitude", 59.7241)
                .property("longitude", 30.4095)
                .property("capacity", 700)
                .property("currentStock", 450)
                .property("securityLevel", "STANDARD")
                .next();
            
            System.out.println("Created all depots, now creating routes...");
            
            // Connect hubs to each other
            addSupplyRouteWithProperties(moscowHub, spbHub, 700.0, 0.2);
            addSupplyRouteWithProperties(moscowHub, novosibirskHub, 3000.0, 0.4);
            addSupplyRouteWithProperties(spbHub, novosibirskHub, 3200.0, 0.5);
            
            // Connect hubs to city depots
            addSupplyRouteWithProperties(moscowHub, kazan, 800.0, 0.3);
            addSupplyRouteWithProperties(moscowHub, samara, 1000.0, 0.3);
            addSupplyRouteWithProperties(novosibirskHub, vladivostok, 5000.0, 0.6);
            
            // Add more connections between city depots
            addSupplyRouteWithProperties(kazan, samara, 400.0, 0.2);
            addSupplyRouteWithProperties(spbHub, kazan, 1500.0, 0.4);
            addSupplyRouteWithProperties(spbHub, samara, 1800.0, 0.5);
            
            // Connect city depots to distribution points
            addSupplyRouteWithProperties(samara, podolsk, 950.0, 0.3);
            addSupplyRouteWithProperties(kazan, pushkin, 1200.0, 0.35);
            
            // Add more connections between distribution points and hubs
            addSupplyRouteWithProperties(moscowHub, podolsk, 50.0, 0.1);
            addSupplyRouteWithProperties(spbHub, pushkin, 30.0, 0.1);
            
            System.out.println("Created all routes");
            
            // Create missile types if they don't exist yet
            createMissileTypeIfNotExists("MT001", "Kinzhal", 2000.0, 100.0);
            createMissileTypeIfNotExists("MT002", "Zircon", 1000.0, 50.0);
            createMissileTypeIfNotExists("MT003", "Sarmat", 18000.0, 1500.0);
            
            try {
                // Add missiles to depots
                addMissilesToDepot("HUB-001", "MT001", 100);
                addMissilesToDepot("HUB-001", "MT002", 50);
                addMissilesToDepot("HUB-002", "MT001", 80);
                addMissilesToDepot("HUB-003", "MT003", 40);
            } catch (Exception e) {
                System.err.println("Error adding missiles to depots: " + e.getMessage());
            }
            
            System.out.println("Sample supply chain generated successfully!");
            
            // Return total count of depots
            return 8;
        } catch (Exception e) {
            System.err.println("Error generating sample supply chain: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    private Vertex createMissileTypeIfNotExists(String id, String name, double range, double effectRadius) {
        try {
            List<Vertex> existing = g.V().has("MissileType", "missileTypeId", id).toList();
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
            return addMissileType(id, name, range, effectRadius);
        } catch (Exception e) {
            System.err.println("Error creating missile type: " + e.getMessage());
            return null;
        }
    }
    
    private Edge addSupplyRouteWithProperties(Vertex source, Vertex target, double distance, double riskFactor) {
        try {
            System.out.println("Creating route from " + source.value("name") + " to " + target.value("name"));
            
            // Explicitly commit before adding the edge
            Edge edge = g.addE("SupplyRoute")
                .from(source)
                .to(target)
                .property("distance", distance)
                .property("riskFactor", riskFactor)
                .property("isActive", true)
                .property("transportType", getTransportTypeByRisk(riskFactor))
                .property("securityLevel", getSecurityLevelByRisk(riskFactor))
                .property("capacity", getCapacityByDistance(distance))
                .next();
                
            System.out.println("Successfully created route: " + edge.id());
            
            // Verify the edge was created
            boolean exists = g.E(edge.id()).hasNext();
            System.out.println("Edge exists after creation: " + exists);
            
            return edge;
        } catch (Exception e) {
            System.err.println("Error creating route from " + source.value("name") + " to " + target.value("name") + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private String getTransportTypeByRisk(double riskFactor) {
        if (riskFactor > 0.4) return "ARMORED_CONVOY";
        if (riskFactor > 0.2) return "SECURE_TRUCK";
        return "LIGHT_VEHICLE";
    }
    
    private String getSecurityLevelByRisk(double riskFactor) {
        if (riskFactor > 0.4) return "HIGH";
        if (riskFactor > 0.2) return "MEDIUM";
        return "STANDARD";
    }
    
    private int getCapacityByDistance(double distance) {
        if (distance > 1000) return 500;
        if (distance > 300) return 800;
        return 1000;
    }
}