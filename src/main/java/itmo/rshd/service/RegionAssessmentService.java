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

    // New helper method to recursively collect users
    private void collectUsersForElimination(Region currentRegion, java.util.Set<User> usersToCollect) {
        if (currentRegion == null) {
            return;
        }

        // Add users directly associated with the currentRegion
        if (currentRegion.getUsers() != null) {
            usersToCollect.addAll(currentRegion.getUsers());
        }

        // If the current region is not a district (i.e., it can have sub-regions),
        // recursively collect users from its sub-regions.
        if (currentRegion.getType() != Region.RegionType.DISTRICT) {
            List<Region> subRegions = regionRepository.findByParentRegionId(currentRegion.getId());
            if (subRegions != null) {
                for (Region subRegion : subRegions) {
                    collectUsersForElimination(subRegion, usersToCollect);
                }
            }
        }
    }

    /**
     * "Eliminates" users in a region after a missile strike.
     * Users are collected from the target region and its sub-regions by traversing the hierarchy
     * and using the embedded user lists within each region object.
     * Eliminated users have their social rating set to 0 and marked inactive.
     * The UserRepository is updated, and the target region's embedded user list is updated.
     */
    private void eliminateUsersInRegion(String regionId) {
        Region region = regionRepository.findById(regionId).orElse(null);
        if (region == null) {
            System.out.println("Warning: Region not found for elimination: " + regionId);
            return;
        }

        java.util.Set<User> uniqueUsersToEliminate = new java.util.LinkedHashSet<>();
        collectUsersForElimination(region, uniqueUsersToEliminate);
        List<User> usersToEliminateList = new ArrayList<>(uniqueUsersToEliminate);

        System.out.println("Total " + usersToEliminateList.size() + " users found to eliminate in " + region.getType() + " " + region.getName());

        List<User> savedEliminatedUsers = new ArrayList<>();
        if (!usersToEliminateList.isEmpty()) {
            for (User user : usersToEliminateList) {
                user.setSocialRating(0);
                user.setActive(false);
                savedEliminatedUsers.add(userRepository.save(user));
            }
            System.out.println("Completed repository update for " + savedEliminatedUsers.size() + " users in " + region.getType() + " " + region.getName());
        } else {
            System.out.println("No users to eliminate in " + region.getType() + " " + region.getName());
        }

        // Update the embedded users list in the TARGET Region object
        // This ensures its direct list is consistent with eliminated users it contained.
        if (region != null && !savedEliminatedUsers.isEmpty()) {
            System.out.println("Updating embedded users list in region: " + region.getName());
            List<User> currentDirectUsersInTargetRegion = region.getUsers();
            if (currentDirectUsersInTargetRegion == null) {
                currentDirectUsersInTargetRegion = new ArrayList<>();
            }

            java.util.Map<String, User> finalStateUserMap = new java.util.HashMap<>();
            for (User seu : savedEliminatedUsers) {
                if (seu.getId() != null) { // Ensure ID is not null before putting in map
                    finalStateUserMap.put(seu.getId(), seu);
                }
            }

            List<User> newDirectUserListForTargetRegion = new ArrayList<>();
            for (User directUser : currentDirectUsersInTargetRegion) {
                if (directUser.getId() != null && finalStateUserMap.containsKey(directUser.getId())) {
                    newDirectUserListForTargetRegion.add(finalStateUserMap.get(directUser.getId()));
                } else {
                    newDirectUserListForTargetRegion.add(directUser); // Keep non-eliminated or ID-less user
                }
            }
            region.setUsers(newDirectUserListForTargetRegion);
            regionRepository.save(region);
            System.out.println("Region " + region.getName() + " saved with updated embedded user list.");
        } else if (region != null && usersToEliminateList.isEmpty()) {
             System.out.println("No users were eliminated, embedded list in " + region.getName() + " remains as is.");
        }
    }
}
