package itmo.rshd.util;

import itmo.rshd.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializer that updates all region statistics when the application starts.
 * This ensures we have accurate population counts for all regions.
 */
@Component
@Order(3) // Run after data generation
public class StatisticsInitializer implements CommandLineRunner {

    private final RegionService regionService;

    @Autowired
    public StatisticsInitializer(RegionService regionService) {
        this.regionService = regionService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Initializing region statistics...");
        regionService.updateAllRegionsStatistics();
        System.out.println("Region statistics initialization complete.");
    }
} 