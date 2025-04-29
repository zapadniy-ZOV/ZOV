package itmo.rshd.service;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import itmo.rshd.model.Region;
import itmo.rshd.model.User;
import itmo.rshd.repository.RegionRepository;
import itmo.rshd.repository.UserRepository;

@Service
public class RegionAssessmentService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;
    
    @Autowired
    private WebSocketService webSocketService;

    public boolean shouldDeployOreshnik(String regionId) {
        // Get the region by ID
        Region region = regionRepository.findById(regionId).orElse(null);
        if (region == null) {
            return false;
        }

        // Check if region has low average rating and no important persons
        // If population is zero, don't deploy (can't divide by zero)
        if (region.getPopulationCount() <= 0) {
            return false;
        }
        
        return region.getAverageSocialRating() < 39
                && region.getImportantPersonsCount() / region.getPopulationCount() < 0.02;
    }

    public boolean shouldDeployOreshnikByCalculation(String regionId) {
        // Get users in the region
        List<User> usersInRegion = userRepository.findByRegionId(regionId);
        if (usersInRegion.isEmpty()) {
            return false;
        }

        // Calculate average social rating manually
        double totalRating = 0;
        for (User user : usersInRegion) {
            totalRating += user.getSocialRating();
        }
        double averageRating = totalRating / usersInRegion.size();

        // Check for important persons in the region
        List<User> importantPersons = userRepository.findImportantPersonsInRegion(regionId);

        return averageRating < 30 && importantPersons.isEmpty();
    }

    public boolean deployOreshnik(String regionId) {
        if (shouldDeployOreshnik(regionId)) {
            // Implementation of missile deployment logic
            System.out.println("ORESHNIK deployed to region: " + regionId);

            try {
                Region region = regionRepository.findById(regionId).orElse(null);
                if (region != null) {
                    // "Eliminate" users in the region and update statistics
                    eliminateUsersInRegion(regionId);
                    
                    // Update the region to reflect elimination
                    region.setPopulationCount(0);
                    region.setAverageSocialRating(0);
                    region.setImportantPersonsCount(0);
                    region.setUnderThreat(false); // No longer under threat since everyone is eliminated
                    
                    // Save region changes
                    regionRepository.save(region);
                    
                    // Notify clients about the eliminated region
                    webSocketService.notifyRegionStatusUpdate(region);
                    System.out.println("Region " + region.getName() + " marked as eliminated, population: " + region.getPopulationCount());
                    
                    // Recursively update parent regions
                    if (region.getParentRegionId() != null && !region.getParentRegionId().isEmpty()) {
                        System.out.println("Updating parent region: " + region.getParentRegionId());
                        updateParentRegionStatistics(region.getParentRegionId());
                    }
                    
                    return true;
                }
            } catch (Exception ex) {
                System.err.println("Error updating region after missile deployment: " + ex.getMessage());
                ex.printStackTrace();
                return false;
            }
        }
        return false;
    }
    
    /**
     * "Eliminates" users in a region after a missile strike
     */
    private void eliminateUsersInRegion(String regionId) {
        Region region = regionRepository.findById(regionId).orElse(null);
        if (region == null) {
            System.out.println("Warning: Region not found for elimination: " + regionId);
            return;
        }
        
        List<User> usersToEliminate = new ArrayList<>();
        
        // Different approach based on region type
        if (region.getType() == Region.RegionType.DISTRICT) {
            // For districts, find users directly in the district
            usersToEliminate = userRepository.findByDistrictId(regionId);
            System.out.println("Found " + usersToEliminate.size() + " users in DISTRICT " + region.getName() + " to eliminate");
        } 
        else if (region.getType() == Region.RegionType.CITY) {
            // For cities, find all districts in the city
            List<Region> districts = regionRepository.findByParentRegionId(regionId);
            System.out.println("Processing CITY " + region.getName() + " with " + districts.size() + " districts");
            
            // Find users in all districts of this city
            for (Region district : districts) {
                List<User> districtUsers = userRepository.findByDistrictId(district.getId());
                usersToEliminate.addAll(districtUsers);
                System.out.println("Found " + districtUsers.size() + " users in district " + district.getName());
            }
            
            // Also add users directly associated with the city (officials, etc.)
            List<User> cityUsers = userRepository.findByRegionId(regionId);
            usersToEliminate.addAll(cityUsers);
            System.out.println("Found " + cityUsers.size() + " users directly in city");
        }
        else if (region.getType() == Region.RegionType.REGION) {
            // For federal regions, find all cities in the region
            List<Region> cities = regionRepository.findByParentRegionId(regionId);
            System.out.println("Processing REGION " + region.getName() + " with " + cities.size() + " cities");
            
            // For each city, find its districts and users
            for (Region city : cities) {
                List<Region> districts = regionRepository.findByParentRegionId(city.getId());
                
                // Add users from each district
                for (Region district : districts) {
                    List<User> districtUsers = userRepository.findByDistrictId(district.getId());
                    usersToEliminate.addAll(districtUsers);
                }
                
                // Add users directly associated with the city
                List<User> cityUsers = userRepository.findByRegionId(city.getId());
                usersToEliminate.addAll(cityUsers);
            }
            
            // Also add users directly associated with the region
            List<User> regionUsers = userRepository.findByRegionId(regionId);
            usersToEliminate.addAll(regionUsers);
        }
        else if (region.getType() == Region.RegionType.COUNTRY) {
            // For country, this is catastrophic - eliminate all users
            usersToEliminate = userRepository.findAll();
            System.out.println("WARNING: Eliminating all users in the country!");
        }
        
        System.out.println("Total " + usersToEliminate.size() + " users found to eliminate in " + region.getType() + " " + region.getName());
        
        // Mark users as eliminated
        int eliminatedCount = 0;
        for (User user : usersToEliminate) {
            user.setSocialRating(0);
            user.setActive(false); // Mark users as eliminated
            userRepository.save(user);
            eliminatedCount++;
            
            if (eliminatedCount % 100 == 0) {
                System.out.println("Eliminated " + eliminatedCount + " users so far...");
            }
        }
        
        System.out.println("Completed elimination of " + eliminatedCount + " users in " + region.getType() + " " + region.getName());
    }
    
    /**
     * Recursively updates parent region statistics after a missile strike
     */
    private void updateParentRegionStatistics(String parentRegionId) {
        if (parentRegionId == null || parentRegionId.isEmpty()) {
            return; // No parent to update
        }
        
        Optional<Region> parentOpt = regionRepository.findById(parentRegionId);
        if (parentOpt.isPresent()) {
            Region parent = parentOpt.get();
            System.out.println("Updating parent region: " + parent.getName());
            
            // Initialize counters
            int totalPopulation = 0;
            double totalRatingSum = 0;
            int totalImportantPersons = 0;
            
            // Different counting strategy based on region type
            if (parent.getType() == Region.RegionType.COUNTRY) {
                // For country, count all active users
                List<User> allUsers = userRepository.findByActive(true);
                totalPopulation = allUsers.size();
                totalRatingSum = allUsers.stream()
                    .mapToDouble(User::getSocialRating)
                    .sum();
                totalImportantPersons = (int) allUsers.stream()
                    .filter(u -> u.getStatus() == User.SocialStatus.IMPORTANT || u.getStatus() == User.SocialStatus.VIP)
                    .count();
            } 
            else if (parent.getType() == Region.RegionType.REGION) {
                // For regions, count users in this region and all its cities and districts
                List<User> regionUsers = userRepository.findByRegionId(parentRegionId);
                totalPopulation = regionUsers.size();
                totalRatingSum = regionUsers.stream()
                    .mapToDouble(User::getSocialRating)
                    .sum();
                totalImportantPersons = (int) regionUsers.stream()
                    .filter(u -> u.getStatus() == User.SocialStatus.IMPORTANT || u.getStatus() == User.SocialStatus.VIP)
                    .count();
            }
            else if (parent.getType() == Region.RegionType.CITY) {
                // For cities, count users in this city and all its districts
                List<User> cityUsers = userRepository.findByRegionId(parentRegionId);
                totalPopulation = cityUsers.size();
                totalRatingSum = cityUsers.stream()
                    .mapToDouble(User::getSocialRating)
                    .sum();
                totalImportantPersons = (int) cityUsers.stream()
                    .filter(u -> u.getStatus() == User.SocialStatus.IMPORTANT || u.getStatus() == User.SocialStatus.VIP)
                    .count();
            }
            else if (parent.getType() == Region.RegionType.DISTRICT) {
                // For districts, just count direct users
                List<User> districtUsers = userRepository.findByDistrictId(parentRegionId);
                totalPopulation = districtUsers.size();
                totalRatingSum = districtUsers.stream()
                    .mapToDouble(User::getSocialRating)
                    .sum();
                totalImportantPersons = (int) districtUsers.stream()
                    .filter(u -> u.getStatus() == User.SocialStatus.IMPORTANT || u.getStatus() == User.SocialStatus.VIP)
                    .count();
            }
            
            // Update parent region statistics
            System.out.println("Before update - Region: " + parent.getName() + 
                              ", Population: " + parent.getPopulationCount() + 
                              ", Rating: " + parent.getAverageSocialRating() + 
                              ", Important: " + parent.getImportantPersonsCount());
            
            parent.setPopulationCount(totalPopulation);
            
            if (totalPopulation > 0) {
                parent.setAverageSocialRating(totalRatingSum / totalPopulation);
            } else {
                parent.setAverageSocialRating(0);
            }
            
            parent.setImportantPersonsCount(totalImportantPersons);
            
            // Re-evaluate if the parent should be under threat only if it still has population
            if (totalPopulation > 0) {
                boolean underThreat = shouldDeployOreshnik(parent.getId());
                parent.setUnderThreat(underThreat);
            } else {
                parent.setUnderThreat(false);
            }
            
            System.out.println("After update - Region: " + parent.getName() + 
                              ", Population: " + parent.getPopulationCount() + 
                              ", Rating: " + parent.getAverageSocialRating() + 
                              ", Important: " + parent.getImportantPersonsCount());
            
            // Save updated parent
            regionRepository.save(parent);
            
            // Notify clients about the region update
            webSocketService.notifyRegionStatusUpdate(parent);
            
            // Continue up the hierarchy
            if (parent.getParentRegionId() != null && !parent.getParentRegionId().isEmpty()) {
                updateParentRegionStatistics(parent.getParentRegionId());
            }
        } else {
            System.out.println("Warning: Parent region not found: " + parentRegionId);
        }
    }
}
