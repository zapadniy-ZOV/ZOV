package itmo.rshd.util;

import com.github.javafaker.Faker;
import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.Region;
import itmo.rshd.model.User;
import itmo.rshd.repository.RegionRepository;
import itmo.rshd.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.geo.Point;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Data generator to populate the database with test data.
 * Run this once to initialize your database with sample data.
 */
@Component
public class DataGenerator implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final Faker faker = new Faker(new Locale("ru"));

    private static final int USERS_COUNT = 120000; // Total users to generate
    private static final double RUSSIA_CENTER_LAT = 55.7558;
    private static final double RUSSIA_CENTER_LON = 37.6173;
    private static final double MIN_VALID_LAT = -90;
    private static final double MAX_VALID_LAT = 90;
    private static final double MIN_VALID_LON = -180;
    private static final double MAX_VALID_LON = 180;

    @Autowired
    public DataGenerator(UserRepository userRepository, RegionRepository regionRepository) {
        this.userRepository = userRepository;
        this.regionRepository = regionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Check if data already exists
        if (userRepository.count() > 0 || regionRepository.count() > 0) {
            System.out.println("Database already populated. Skipping data generation.");
            return;
        }

        System.out.println("Starting data generation...");

        // Create the country
        Region russia = createCountry();

        // Create 8 federal regions
        List<Region> federalRegions = createFederalRegions(russia);

        // Create 30 cities across all regions
        List<Region> cities = createCities(federalRegions);

        // Create 100 districts across all cities
        List<Region> districts = createDistricts(cities);

        // Create users and distribute them across districts
        createUsers(districts, cities, federalRegions, russia);

        // Update region statistics
        updateRegionStatistics(districts, cities, federalRegions, russia);

        System.out.println("Data generation completed!");
    }

    private Region createCountry() {
        Region russia = new Region();
        russia.setName("Russia");
        russia.setType(Region.RegionType.COUNTRY);
        russia.setAverageSocialRating(65);
        russia.setPopulationCount(0);
        russia.setImportantPersonsCount(0);
        russia.setUnderThreat(false);

        // Detailed coordinates for Russia's boundaries
        List<Point> polygonPoints = Arrays.asList(
                new Point(30.0, 59.0),
                new Point(180.0, 59.0),
                new Point(180.0, 44.0),
                new Point(30.0, 44.0),
                new Point(30.0, 59.0) // Closing the polygon
        );

        russia.setBoundaries(new GeoJsonPolygon(polygonPoints));

        return regionRepository.save(russia);
    }

    private List<Region> createFederalRegions(Region country) {
        String[] regionNames = {
                "Central Federal District",
                "Northwestern Federal District",
                "Southern Federal District",
                "North Caucasian Federal District",
                "Volga Federal District",
                "Ural Federal District",
                "Siberian Federal District",
                "Far Eastern Federal District"
        };

        // Base coordinates for each federal region
        double[][] regionCoords = {
                { 55.75, 37.61 }, // Central (Moscow)
                { 59.93, 30.31 }, // Northwestern (St. Petersburg)
                { 47.23, 39.70 }, // Southern (Rostov)
                { 43.02, 44.68 }, // North Caucasian (Stavropol)
                { 55.79, 49.11 }, // Volga (Kazan)
                { 56.83, 60.61 }, // Ural (Yekaterinburg)
                { 55.01, 82.93 }, // Siberian (Novosibirsk)
                { 43.12, 131.89 } // Far Eastern (Vladivostok)
        };

        List<Region> regions = new ArrayList<>();

        for (int i = 0; i < regionNames.length; i++) {
            Region region = new Region();
            region.setName(regionNames[i]);
            region.setType(Region.RegionType.REGION);
            region.setParentRegionId(country.getId());

            // Create more varied regional ratings
            double socialRating;
            if (i % 3 == 0) {
                // Every third region is "bad" with low social rating
                socialRating = ThreadLocalRandom.current().nextDouble(35, 45);
                region.setUnderThreat(true);
            } else if (i % 3 == 1) {
                // Every third region + 1 is "normal"
                socialRating = ThreadLocalRandom.current().nextDouble(50, 70);
                region.setUnderThreat(false);
            } else {
                // Every third region + 2 is "good"
                socialRating = ThreadLocalRandom.current().nextDouble(75, 90);
                region.setUnderThreat(false);
            }

            region.setAverageSocialRating(socialRating);
            region.setPopulationCount(0);
            region.setImportantPersonsCount(0);

            // Create GeoJsonPolygon boundaries
            GeoJsonPolygon boundaries = createBoundaries(regionCoords[i][0], regionCoords[i][1], 5.0);
            region.setBoundaries(boundaries);

            regions.add(regionRepository.save(region));
        }

        return regions;
    }

    private List<Region> createCities(List<Region> federalRegions) {
        List<Region> cities = new ArrayList<>();

        // Create several cities for each federal region
        for (Region federalRegion : federalRegions) {
            // Number of cities per region - random between 3 and 6
            int cityCount = ThreadLocalRandom.current().nextInt(3, 7);

            for (int i = 0; i < cityCount; i++) {
                Region city = new Region();

                // Balance city types: 20% bad, 60% normal, 20% good
                double cityType = ThreadLocalRandom.current().nextDouble();
                double socialRating;
                String namePrefix = "";

                if (cityType < 0.2) {
                    // "Bad" city - lower social rating
                    socialRating = ThreadLocalRandom.current().nextDouble(30, 45);
                    namePrefix = "Депрессивный ";
                } else if (cityType < 0.8) {
                    // "Normal" city - average social rating
                    socialRating = ThreadLocalRandom.current().nextDouble(50, 70);
                } else {
                    // "Good" city - high social rating
                    socialRating = ThreadLocalRandom.current().nextDouble(75, 90);
                    namePrefix = "Процветающий ";
                }

                city.setName(namePrefix + faker.address().city());
                city.setType(Region.RegionType.CITY);
                city.setParentRegionId(federalRegion.getId());
                city.setAverageSocialRating(socialRating);
                city.setPopulationCount(0);
                city.setImportantPersonsCount(0);

                // Some cities with very low ratings are under threat
                city.setUnderThreat(socialRating < 39);

                // Get a random point within the federal region's boundaries
                GeoLocation center = getRandomPointInBoundaries(federalRegion.getBoundaries());

                // Create city boundaries (smaller than federal regions)
                double size = 0.2;
                GeoJsonPolygon boundaries = createBoundaries(center.getLatitude(), center.getLongitude(), size);
                city.setBoundaries(boundaries);

                cities.add(regionRepository.save(city));
            }
        }

        return cities;
    }

    private List<Region> createDistricts(List<Region> cities) {
        List<Region> districts = new ArrayList<>();

        // Create districts for each city
        for (Region city : cities) {
            // Number of districts per city - random between 2 and 5
            int districtCount = ThreadLocalRandom.current().nextInt(2, 6);

            for (int i = 0; i < districtCount; i++) {
                Region district = new Region();

                // Balance district types: 30% bad, 50% normal, 20% good
                double districtType = ThreadLocalRandom.current().nextDouble();
                double socialRating;
                String namePrefix;

                if (districtType < 0.3) {
                    // "Bad" district - low social rating, eligible for missile
                    socialRating = ThreadLocalRandom.current().nextDouble(20, 32);
                    namePrefix = "Проблемный ";
                    district.setUnderThreat(true);
                } else if (districtType < 0.8) {
                    // "Normal" district - average social rating
                    socialRating = ThreadLocalRandom.current().nextDouble(45, 70);
                    namePrefix = "";
                    district.setUnderThreat(false);
                } else {
                    // "Good" district - high social rating
                    socialRating = ThreadLocalRandom.current().nextDouble(75, 95);
                    namePrefix = "Образцовый ";
                    district.setUnderThreat(false);
                }

                district.setName(namePrefix + faker.address().streetName() + " District");
                district.setType(Region.RegionType.DISTRICT);
                district.setParentRegionId(city.getId());
                district.setAverageSocialRating(socialRating);
                district.setPopulationCount(0);

                // Distribute important persons based on social rating
                // Higher social rating districts have more important persons
                if (socialRating > 70) {
                    district.setImportantPersonsCount(ThreadLocalRandom.current().nextInt(2, 5));
                } else if (socialRating > 40) {
                    district.setImportantPersonsCount(ThreadLocalRandom.current().nextInt(0, 2));
                } else {
                    // Low social rating districts have no important persons
                    district.setImportantPersonsCount(0);
                }

                // Get a random point within the city's boundaries
                GeoLocation center = getRandomPointInBoundaries(city.getBoundaries());

                // Create district boundaries (smaller than cities)
                double size = 0.05;
                GeoJsonPolygon boundaries = createBoundaries(center.getLatitude(), center.getLongitude(), size);
                district.setBoundaries(boundaries);

                districts.add(regionRepository.save(district));
            }
        }

        return districts;
    }

    private void createUsers(List<Region> districts, List<Region> cities, List<Region> federalRegions, Region country) {
        List<User> users = new ArrayList<>();

        // Create country president
        User president = createSpecialUser(country, null, null, User.SocialStatus.VIP, 95, 100);
        users.add(president);

        // Create federal region heads
        for (Region federalRegion : federalRegions) {
            User regionalHead = createSpecialUser(country, federalRegion, null, User.SocialStatus.VIP, 85, 95);
            users.add(regionalHead);
        }

        // Create city mayors
        for (Region city : cities) {
            // Find parent region
            String regionId = city.getParentRegionId();
            User mayor = createSpecialUser(country,
                    findRegionById(federalRegions, regionId),
                    city,
                    User.SocialStatus.IMPORTANT, 75, 85);
            users.add(mayor);
        }

        // Create regular users and distribute across districts
        for (int i = users.size(); i < USERS_COUNT; i++) {
            // Select random district
            Region district = districts.get(ThreadLocalRandom.current().nextInt(districts.size()));

            // Find parent city and region
            Region city = findRegionById(cities, district.getParentRegionId());
            Region federalRegion = findRegionById(federalRegions, city.getParentRegionId());

            // Determine status and social rating based on district rating
            User.SocialStatus status;
            double minRating, maxRating;

            // Distribute social statuses based on district's social rating
            double districtRating = district.getAverageSocialRating();
            double random = ThreadLocalRandom.current().nextDouble();

            if (districtRating < 39) {
                // Low-rated district: 60% LOW, 40% REGULAR, 0% IMPORTANT, 0% VIP
                if (random < 0.6) {
                    status = User.SocialStatus.LOW;
                } else {
                    status = User.SocialStatus.REGULAR;
                }
            } else if (districtRating < 70) {
                // Mid-rated district: 20% LOW, 75% REGULAR, 5% IMPORTANT, 0% VIP
                if (random < 0.2) {
                    status = User.SocialStatus.LOW;
                } else if (random < 0.95) {
                    status = User.SocialStatus.REGULAR;
                } else {
                    status = User.SocialStatus.IMPORTANT;
                }
            } else {
                // High-rated district: 5% LOW, 80% REGULAR, 14% IMPORTANT, 1% VIP
                if (random < 0.05) {
                    status = User.SocialStatus.LOW;
                } else if (random < 0.85) {
                    status = User.SocialStatus.REGULAR;
                } else if (random < 0.99) {
                    status = User.SocialStatus.IMPORTANT;
                } else {
                    status = User.SocialStatus.VIP;
                }
            }

            // Define rating range based on status
            switch (status) {
                case LOW:
                    minRating = 10;
                    maxRating = 39;
                    break;
                case REGULAR:
                    minRating = 40;
                    maxRating = 69;
                    break;
                case IMPORTANT:
                    minRating = 70;
                    maxRating = 89;
                    break;
                case VIP:
                    minRating = 90;
                    maxRating = 100;
                    break;
                default:
                    minRating = 40;
                    maxRating = 69;
            }

            // Create the user
            User user = createUser(country, federalRegion, city, district, status, minRating, maxRating);
            users.add(user);

            // Save in batches to avoid memory issues
            if (users.size() % 100 == 0) {
                userRepository.saveAll(users);
                users.clear();
                System.out.println("Generated " + i + " users so far...");
            }
        }

        // Save any remaining users
        if (!users.isEmpty()) {
            userRepository.saveAll(users);
        }
    }

    private User createSpecialUser(Region country, Region federalRegion, Region city,
            User.SocialStatus status, double minRating, double maxRating) {
        User user = new User();
        // Generate unique username with timestamp and random number
        String uniqueUsername = faker.name().firstName().toLowerCase() +
                "." + faker.name().lastName().toLowerCase() +
                "_" + System.currentTimeMillis() % 100000 +
                faker.random().nextInt(1000, 9999);
        user.setUsername(uniqueUsername);
        user.setPassword(faker.internet().password(8, 12));

        // Special users have "important" titles in their names
        String title = "";
        if (federalRegion == null) {
            title = "President ";
        } else if (city == null) {
            title = "Governor ";
        } else {
            title = "Mayor ";
        }

        user.setFullName(title + faker.name().fullName());
        user.setSocialRating(ThreadLocalRandom.current().nextDouble(minRating, maxRating));
        user.setStatus(status);
        user.setActive(true);
        user.setLastLocationUpdateTimestamp(System.currentTimeMillis());

        // Set country
        user.setCountryId(country.getId());

        // Set region if provided
        if (federalRegion != null) {
            user.setRegionId(federalRegion.getId());
        } else {
            // Randomly select a region for the president
            user.setRegionId("none"); // Special case for president
        }

        // Set city if provided
        if (city != null) {
            // For city officials, set the regionId to the cityId to properly associate them
            user.setRegionId(city.getId());
            user.setDistrictId("none"); // City official

            // Location in the city center
            GeoLocation cityCenter = getCenterPoint(city.getBoundaries());
            user.setCurrentLocation(cityCenter);
        } else if (federalRegion != null) {
            user.setDistrictId("none"); // Regional official

            // Location in the region center
            GeoLocation regionCenter = getCenterPoint(federalRegion.getBoundaries());
            user.setCurrentLocation(regionCenter);
        } else {
            user.setDistrictId("none"); // National official

            // Capital location
            user.setCurrentLocation(createValidGeoLocation(RUSSIA_CENTER_LAT, RUSSIA_CENTER_LON));
        }

        return user;
    }

    private User createUser(Region country, Region federalRegion, Region city, Region district,
            User.SocialStatus status, double minRating, double maxRating) {
        User user = new User();
        // Generate unique username with timestamp and random number
        String uniqueUsername = faker.name().firstName().toLowerCase() +
                "." + faker.name().lastName().toLowerCase() +
                "_" + System.currentTimeMillis() % 100000 +
                faker.random().nextInt(1000, 9999);
        user.setUsername(uniqueUsername);
        user.setPassword(faker.internet().password(8, 12));
        user.setFullName(faker.name().fullName());
        user.setSocialRating(ThreadLocalRandom.current().nextDouble(minRating, maxRating));
        user.setStatus(status);
        user.setActive(true);
        user.setLastLocationUpdateTimestamp(System.currentTimeMillis());

        // Set geographical IDs
        user.setCountryId(country.getId());
        user.setRegionId(federalRegion.getId());
        user.setDistrictId(district.getId());

        // Random location within district boundaries
        GeoLocation location = getRandomPointInBoundaries(district.getBoundaries());
        user.setCurrentLocation(location);

        return user;
    }

    private void updateRegionStatistics(List<Region> districts, List<Region> cities,
            List<Region> federalRegions, Region country) {
        System.out.println("Updating region statistics...");

        // Update district statistics
        for (Region district : districts) {
            updateDistrictStatistics(district);
        }

        // Update city statistics based on districts
        for (Region city : cities) {
            updateHigherRegionStatistics(city, districts);
        }

        // Update federal region statistics based on cities
        for (Region federalRegion : federalRegions) {
            updateHigherRegionStatistics(federalRegion, cities);
        }

        // Update country statistics based on federal regions
        updateHigherRegionStatistics(country, federalRegions);

        // Save all updated regions
        regionRepository.saveAll(districts);
        regionRepository.saveAll(cities);
        regionRepository.saveAll(federalRegions);
        regionRepository.save(country);

        System.out.println("Region statistics updated.");
    }

    private void updateDistrictStatistics(Region district) {
        List<User> usersInDistrict = userRepository.findByDistrictId(district.getId());

        // No users in this district
        if (usersInDistrict.isEmpty()) {
            district.setPopulationCount(0);
            district.setImportantPersonsCount(0);
            district.setAverageSocialRating(50); // Default rating
            return;
        }

        // Set population count
        district.setPopulationCount(usersInDistrict.size());

        // Calculate average social rating
        double totalRating = 0;
        int importantCount = 0;

        for (User user : usersInDistrict) {
            totalRating += user.getSocialRating();

            if (user.getStatus() == User.SocialStatus.IMPORTANT ||
                    user.getStatus() == User.SocialStatus.VIP) {
                importantCount++;
            }
        }

        district.setAverageSocialRating(totalRating / usersInDistrict.size());
        district.setImportantPersonsCount(importantCount);

        // Update threat status based on social rating and important persons
        boolean isUnderThreat = district.getAverageSocialRating() < 40 && importantCount == 0;
        district.setUnderThreat(isUnderThreat);
    }

    private void updateHigherRegionStatistics(Region region, List<Region> subRegions) {
        // Filter sub-regions that belong to this region
        List<Region> children = subRegions.stream()
                .filter(r -> r.getParentRegionId().equals(region.getId()))
                .collect(Collectors.toList());

        if (children.isEmpty()) {
            return;
        }

        int totalPopulation = 0;
        int totalImportantPersons = 0;
        double weightedRatingSum = 0;

        for (Region child : children) {
            totalPopulation += child.getPopulationCount();
            totalImportantPersons += child.getImportantPersonsCount();

            // Weighted average based on population
            weightedRatingSum += child.getAverageSocialRating() * child.getPopulationCount();
        }

        region.setPopulationCount(totalPopulation);
        region.setImportantPersonsCount(totalImportantPersons);

        if (totalPopulation > 0) {
            region.setAverageSocialRating(weightedRatingSum / totalPopulation);
        }

        // A region is under threat if its average rating is low and has no important
        // persons
        boolean isUnderThreat = region.getAverageSocialRating() < 40 && totalImportantPersons == 0;
        region.setUnderThreat(isUnderThreat);
    }

    // Helper to find a region by ID in a list of regions
    private Region findRegionById(List<Region> regions, String id) {
        return regions.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Helper to create boundaries around a center point
    private GeoJsonPolygon createBoundaries(double centerLat, double centerLon, double size) {
        List<Point> polygonPoints = Arrays.asList(
                new Point(centerLon - size, centerLat + size), // Northwest
                new Point(centerLon + size, centerLat + size), // Northeast
                new Point(centerLon + size, centerLat - size), // Southeast
                new Point(centerLon - size, centerLat - size), // Southwest
                new Point(centerLon - size, centerLat + size) // Closing the polygon
        );

        return new GeoJsonPolygon(polygonPoints);
    }

    // Helper to get a random point within given boundaries
    private GeoLocation getRandomPointInBoundaries(GeoJsonPolygon boundaries) {
        List<Point> points = boundaries.getPoints(); // Get points directly from GeoJsonPolygon

        if (points.size() < 4) { // Must have at least four points to form a polygon
            // Default to center of Russia if boundaries not properly defined
            return createValidGeoLocation(RUSSIA_CENTER_LAT, RUSSIA_CENTER_LON);
        }

        // Find min/max coordinates
        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;

        for (Point point : points) {
            double lon = point.getX(); // Longitude first in GeoJSON
            double lat = point.getY();
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        // Ensure coordinates are within valid range
        minLat = Math.max(minLat, -90);
        maxLat = Math.min(maxLat, 90);
        minLon = Math.max(minLon, -180);
        maxLon = Math.min(maxLon, 180);

        // Generate random coordinates within the bounding box
        double lat = minLat + (maxLat - minLat) * ThreadLocalRandom.current().nextDouble();
        double lon = minLon + (maxLon - minLon) * ThreadLocalRandom.current().nextDouble();

        // Use our validator helper to ensure coordinates are valid
        return createValidGeoLocation(lat, lon);
    }

    // Helper to calculate center point of a polygon
    private GeoLocation getCenterPoint(GeoJsonPolygon boundaries) {
        List<Point> points = boundaries.getPoints(); // Get points directly from GeoJsonPolygon

        if (points.size() < 4) { // Must have at least four points to form a polygon
            // Default to center of Russia if boundaries not properly defined
            return createValidGeoLocation(RUSSIA_CENTER_LAT, RUSSIA_CENTER_LON);
        }

        double sumLat = 0, sumLon = 0;
        for (Point point : points) {
            sumLat += point.getY(); // Latitude
            sumLon += point.getX(); // Longitude
        }

        double avgLat = sumLat / points.size();
        double avgLon = sumLon / points.size();

        // Use our validator helper to ensure coordinates are valid
        return createValidGeoLocation(avgLat, avgLon);
    }

    /**
     * Validates and sanitizes geographic coordinates to ensure they're within valid
     * ranges
     */
    private GeoLocation createValidGeoLocation(double latitude, double longitude) {
        double validLat = Math.max(MIN_VALID_LAT, Math.min(MAX_VALID_LAT, latitude));
        double validLon = Math.max(MIN_VALID_LON, Math.min(MAX_VALID_LON, longitude));

        // Log if coordinates were adjusted
        if (validLat != latitude || validLon != longitude) {
            System.out.println("Warning: Adjusted invalid coordinates from (" +
                    latitude + ", " + longitude + ") to (" + validLat + ", " + validLon + ")");
        }

        return new GeoLocation(validLat, validLon);
    }
}
