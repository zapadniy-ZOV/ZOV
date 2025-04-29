package itmo.rshd.config;

import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV3;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

@Configuration
public class JanusGraphConfig {

    private Cluster cluster;
    private GraphTraversalSource g;

    @Bean
    public GraphTraversalSource graphTraversalSource() {
        // Configure the serializer to handle JanusGraph types
        // Using GraphSON instead of GraphBinary for better compatibility
        final GraphSONMessageSerializerV3 serializer = new GraphSONMessageSerializerV3();
        Map<String, Object> config = new HashMap<>();
        config.put("ioRegistries", Arrays.asList("org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry"));
        serializer.configure(config, null);

        // Create connection to the remote JanusGraph server
        cluster = Cluster.build()
                .addContactPoint("localhost")
                .port(8182)
                .serializer(serializer) // Use the configured serializer
                .create();
        
        // Get traversal source
        g = traversal().withRemote(DriverRemoteConnection.using(cluster, "g"));
        
        return g;
    }

    @PreDestroy
    public void closeGraph() throws IOException {
        try {
            if (g != null) {
                g.close();
                System.out.println("Graph traversal source closed properly.");
            }
            
            if (cluster != null) {
                cluster.close();
                System.out.println("Gremlin cluster connection closed properly.");
            }
        } catch (Exception e) {
            System.err.println("Error while closing JanusGraph connections: " + e.getMessage());
            throw new IOException("Failed to close JanusGraph connections", e);
        }
    }
}