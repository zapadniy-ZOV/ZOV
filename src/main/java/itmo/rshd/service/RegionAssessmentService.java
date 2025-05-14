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

    @Autowired
    private RegionService regionService;

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
        Optional<Region> regionOpt = regionRepository.findById(regionId);
        if (!regionOpt.isPresent()) {
            System.err.println("ORESHNIK DEPLOYMENT FAILED: Region not found: " + regionId);
            return false;
        }
        Region region = regionOpt.get();

        if (shouldDeployOreshnik(regionId)) {
            System.out.println("ORESHNIK deployment authorized for region: " + region.getName() + " (ID: " + regionId + ")");

            eliminateUsersInRegion(regionId); // This now updates embedded users and saves the region.
            Region updatedRegionAfterElimination = regionService.updateRegionStatistics(regionId);

            if (updatedRegionAfterElimination != null) {
                System.out.println("Region " + updatedRegionAfterElimination.getName() +
                                   " statistics updated after Oreshnik. Population: " + updatedRegionAfterElimination.getPopulationCount() +
                                   ", AvgRating: " + updatedRegionAfterElimination.getAverageSocialRating());
                webSocketService.notifyRegionStatusUpdate(updatedRegionAfterElimination);

                String parentId = updatedRegionAfterElimination.getParentRegionId();
                if (parentId != null && !parentId.isEmpty() && !parentId.equals("none")) {
                    System.out.println("Triggering statistics update for parent region: " + parentId);
                    updateParentStatsRecursively(parentId); // New recursive helper using RegionService
                }
                return true;
            } else {
                System.err.println("CRITICAL: Failed to update statistics for region: " + regionId + " after elimination.");
                return false; // Critical failure if stats can't be updated post-elimination.
            }
        } else {
            System.out.println("ORESHNIK deployment not authorized for region: " + region.getName() + " (ID: " + regionId + ")");
            return false;
        }
    }
    
    // New recursive helper to update parent stats using RegionService.updateRegionStatistics
    private void updateParentStatsRecursively(String regionIdToUpdate) {
        if (regionIdToUpdate == null || regionIdToUpdate.isEmpty() || regionIdToUpdate.equals("none")) {
            return;
        }
        System.out.println("Recursively updating stats for: " + regionIdToUpdate);
        Region updatedRegion = regionService.updateRegionStatistics(regionIdToUpdate);
        if (updatedRegion != null) {
            webSocketService.notifyRegionStatusUpdate(updatedRegion); // Notify update for this parent
            // Continue up the hierarchy
            String parentId = updatedRegion.getParentRegionId();
            updateParentStatsRecursively(parentId); // Recursive call
        } else {
            System.err.println("Failed to update stats for parent region: " + regionIdToUpdate + " during recursive update.");
        }
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
        
        if (region.getType() == Region.RegionType.DISTRICT) {
            usersToEliminate = userRepository.findByDistrictId(regionId);
            System.out.println("Found " + usersToEliminate.size() + " users in DISTRICT " + region.getName() + " to eliminate");
        } 
        else if (region.getType() == Region.RegionType.CITY) {
            List<Region> districts = regionRepository.findByParentRegionId(regionId);
            System.out.println("Processing CITY " + region.getName() + " with " + districts.size() + " districts");
            
            for (Region district : districts) {
                List<User> districtUsers = userRepository.findByDistrictId(district.getId());
                usersToEliminate.addAll(districtUsers);
                System.out.println("Found " + districtUsers.size() + " users in district " + district.getName());
            }
            
            List<User> cityUsers = userRepository.findByRegionId(regionId);
            usersToEliminate.addAll(cityUsers);
            System.out.println("Found " + cityUsers.size() + " users directly in city");
        }
        else if (region.getType() == Region.RegionType.REGION) {
            List<Region> cities = regionRepository.findByParentRegionId(regionId);
            System.out.println("Processing REGION " + region.getName() + " with " + cities.size() + " cities");
            
            for (Region city : cities) {
                List<Region> districts = regionRepository.findByParentRegionId(city.getId());
                
                for (Region district : districts) {
                    List<User> districtUsers = userRepository.findByDistrictId(district.getId());
                    usersToEliminate.addAll(districtUsers);
                }
                
                List<User> cityUsers = userRepository.findByRegionId(city.getId());
                usersToEliminate.addAll(cityUsers);
            }
            
            List<User> regionUsers = userRepository.findByRegionId(regionId);
            usersToEliminate.addAll(regionUsers);
        }
        else if (region.getType() == Region.RegionType.COUNTRY) {
            usersToEliminate = userRepository.findAll();
            System.out.println("WARNING: Eliminating all users in the country!");
        }
        
        System.out.println("Total " + usersToEliminate.size() + " users found to eliminate in " + region.getType() + " " + region.getName());
        
        List<User> savedEliminatedUsers = new ArrayList<>();
        if (!usersToEliminate.isEmpty()) {
            for (User user : usersToEliminate) {
                user.setSocialRating(0);
                user.setActive(false);
                savedEliminatedUsers.add(userRepository.save(user));
            }
            System.out.println("Completed repository update for " + savedEliminatedUsers.size() + " users in " + region.getType() + " " + region.getName());
        } else {
            System.out.println("No users to eliminate in " + region.getType() + " " + region.getName());
        }
        
        // Update the embedded users list in the Region object
        if (region != null && !savedEliminatedUsers.isEmpty()) {
            System.out.println("Updating embedded users list in region: " + region.getName());
            List<User> currentEmbeddedUsers = region.getUsers();
            if (currentEmbeddedUsers == null) {
                currentEmbeddedUsers = new ArrayList<>();
            }
            List<User> newEmbeddedUserList = new ArrayList<>();
            for (User embeddedUser : currentEmbeddedUsers) {
                boolean foundInEliminated = false;
                for (User savedEliminatedUser : savedEliminatedUsers) {
                    if (embeddedUser.getId().equals(savedEliminatedUser.getId())) {
                        newEmbeddedUserList.add(savedEliminatedUser);
                        foundInEliminated = true;
                        break;
                    }
                }
                if (!foundInEliminated) {
                    newEmbeddedUserList.add(embeddedUser); // Keep user if not in the elimination list
                }
            }
            region.setUsers(newEmbeddedUserList);
            regionRepository.save(region); // Save the region with the updated embedded user list
            System.out.println("Region " + region.getName() + " saved with updated embedded user list.");
        } else if (region != null && usersToEliminate.isEmpty()) {
             System.out.println("No users were eliminated, embedded list in " + region.getName() + " remains as is.");
        }
    }
}
