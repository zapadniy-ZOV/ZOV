package itmo.rshd.service;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.stream.Collectors;

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

    // Helper method to recursively update statistics of children regions (bottom-up)
    private void recursivelyUpdateStatsOfChildren(Region parentRegion) {
        if (parentRegion == null || parentRegion.getType() == Region.RegionType.DISTRICT) {
            // Districts are leaves in the region hierarchy for statistics calculation purposes here
            return;
        }
        List<Region> children = regionRepository.findByParentRegionId(parentRegion.getId());
        if (children != null) {
            for (Region child : children) {
                recursivelyUpdateStatsOfChildren(child); // Go to deepest children first
                System.out.println("Updating stats for child region: " + child.getName() + " (ID: " + child.getId() + ") before parent " + parentRegion.getName());
                Region updatedChild = regionService.updateRegionStatistics(child.getId());
                if (updatedChild != null) {
                    webSocketService.notifyRegionStatusUpdate(updatedChild);
                } else {
                     System.err.println("Failed to update stats for child region: " + child.getId() + " during recursive update of children for " + parentRegion.getId());
                }
            }
        }
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

            eliminateUsersInRegion(regionId); // This now updates users in DB AND embedded lists in ALL affected regions.
            
            System.out.println("Starting recursive stats update for children of target region: " + region.getName());
            recursivelyUpdateStatsOfChildren(region);
            System.out.println("Finished recursive stats update for children of target region: " + region.getName());

            // Now update the target region itself, it will use the freshly updated stats of its children
            Region updatedRegionAfterEliminationAndChildUpdates = regionService.updateRegionStatistics(regionId);

            if (updatedRegionAfterEliminationAndChildUpdates != null) {
                System.out.println("Region " + updatedRegionAfterEliminationAndChildUpdates.getName() +
                                   " statistics updated after Oreshnik and child updates. Population: " + updatedRegionAfterEliminationAndChildUpdates.getPopulationCount() +
                                   ", AvgRating: " + updatedRegionAfterEliminationAndChildUpdates.getAverageSocialRating());
                webSocketService.notifyRegionStatusUpdate(updatedRegionAfterEliminationAndChildUpdates);

                String parentId = updatedRegionAfterEliminationAndChildUpdates.getParentRegionId();
                if (parentId != null && !parentId.isEmpty() && !parentId.equals("none")) {
                    System.out.println("Triggering statistics update for parent region hierarchy: " + parentId);
                    // This existing method handles recursive updates *upwards* from the target region's parent
                    updateParentStatsRecursively(parentId); 
                }
                return true;
            } else {
                System.err.println("CRITICAL: Failed to update statistics for target region: " + regionId + " after elimination and child updates.");
                return false; 
            }
        } else {
            System.out.println("ORESHNIK deployment not authorized for region: " + region.getName() + " (ID: " + regionId + ")");
            return false;
        }
    }
    
    // This method updates parent stats recursively upwards.
    private void updateParentStatsRecursively(String regionIdToUpdate) {
        if (regionIdToUpdate == null || regionIdToUpdate.isEmpty() || regionIdToUpdate.equals("none")) {
            return;
        }
        System.out.println("Recursively updating stats for ancestor: " + regionIdToUpdate);
        Region updatedRegion = regionService.updateRegionStatistics(regionIdToUpdate);
        if (updatedRegion != null) {
            webSocketService.notifyRegionStatusUpdate(updatedRegion); 
            String parentId = updatedRegion.getParentRegionId();
            updateParentStatsRecursively(parentId); 
        } else {
            System.err.println("Failed to update stats for parent region: " + regionIdToUpdate + " during recursive ancestor update.");
        }
    }

    // New helper method to recursively collect users and involved regions
    private void collectUsersAndRegionsRecursively(Region currentRegion, Set<User> usersToCollect, Map<String, Region> regionsInvolved) {
        if (currentRegion == null) {
            return;
        }
        // Add current region to the map of involved regions
        regionsInvolved.put(currentRegion.getId(), currentRegion);

        // Add users directly associated with the currentRegion
        if (currentRegion.getUsers() != null) {
            usersToCollect.addAll(currentRegion.getUsers());
        }

        // If the current region is not a district, recursively collect from sub-regions.
        if (currentRegion.getType() != Region.RegionType.DISTRICT) {
            List<Region> subRegions = regionRepository.findByParentRegionId(currentRegion.getId());
            if (subRegions != null) {
                for (Region subRegion : subRegions) {
                    // Pass the original maps/sets to accumulate
                    collectUsersAndRegionsRecursively(subRegion, usersToCollect, regionsInvolved);
                }
            }
        }
    }

    /**
     * "Eliminates" users in a target region and its sub-regions.
     * 1. Collects all users from the target region and its descendants.
     * 2. Collects all Region objects involved.
     * 3. Marks collected users as inactive (social rating 0) and saves them to UserRepository.
     * 4. For each involved Region object, updates its embedded 'users' list with the modified User instances and saves the Region.
     */
    private void eliminateUsersInRegion(String targetRegionId) {
        Region targetRegion = regionRepository.findById(targetRegionId).orElse(null);
        if (targetRegion == null) {
            System.out.println("Warning: Target region not found for elimination: " + targetRegionId);
            return;
        }

        Set<User> uniqueUsersToEliminate = new LinkedHashSet<>();
        Map<String, Region> involvedRegionsMap = new HashMap<>();
        
        // Populate usersToEliminate and involvedRegionsMap
        collectUsersAndRegionsRecursively(targetRegion, uniqueUsersToEliminate, involvedRegionsMap);
        
        List<User> usersToEliminateList = new ArrayList<>(uniqueUsersToEliminate);

        System.out.println("Found " + usersToEliminateList.size() + " unique users across " + involvedRegionsMap.size() + " regions (target and sub-regions) for elimination based on target: " + targetRegion.getName());

        List<User> savedBatchOfEliminatedUsers = new ArrayList<>();
        if (!usersToEliminateList.isEmpty()) {
            for (User user : usersToEliminateList) {
                user.setSocialRating(0);
                user.setActive(false);
                // Collect for batch save or save individually - assuming save returns the managed entity
                savedBatchOfEliminatedUsers.add(userRepository.save(user)); 
            }
            System.out.println("Completed UserRepository update for " + savedBatchOfEliminatedUsers.size() + " users.");
        } else {
            System.out.println("No users found to eliminate for target region: " + targetRegion.getName());
            // Still proceed to update regions in case their lists need cleaning, though unlikely if no users.
        }
        
        // Create a map of the final state of eliminated users by their ID for easy lookup
        Map<String, User> finalStateUserMap = savedBatchOfEliminatedUsers.stream()
                                                .filter(u -> u.getId() != null)
                                                .collect(Collectors.toMap(User::getId, u -> u));

        // Update embedded user lists in ALL involved regions
        System.out.println("Updating embedded user lists for all " + involvedRegionsMap.size() + " involved regions...");
        for (Region affectedRegion : involvedRegionsMap.values()) {
            List<User> currentEmbeddedUsers = affectedRegion.getUsers(); // Get original list
            List<User> newEmbeddedUserList = new ArrayList<>();

            if (currentEmbeddedUsers != null) {
                for (User embeddedUser : currentEmbeddedUsers) {
                    if (embeddedUser.getId() != null && finalStateUserMap.containsKey(embeddedUser.getId())) {
                        // If this user was in the elimination batch, add its updated state
                        newEmbeddedUserList.add(finalStateUserMap.get(embeddedUser.getId()));
                    } else {
                        newEmbeddedUserList.add(embeddedUser);
                    }
                }
            }
            List<User> correctlyUpdatedEmbeddedList = new ArrayList<>();
            if (currentEmbeddedUsers != null) {
                for (User originalEmbeddedUser : currentEmbeddedUsers) {
                    User updatedVersion = finalStateUserMap.get(originalEmbeddedUser.getId());
                    if (updatedVersion != null) {
                        correctlyUpdatedEmbeddedList.add(updatedVersion); // Add the updated user from saved batch
                    } else {
                        correctlyUpdatedEmbeddedList.add(originalEmbeddedUser);
                    }
                }
            }


            affectedRegion.setUsers(correctlyUpdatedEmbeddedList); // Use the more robustly built list
            regionRepository.save(affectedRegion);
            System.out.println("Saved region " + affectedRegion.getName() + " (ID: " + affectedRegion.getId() + ") with updated embedded user list (" + correctlyUpdatedEmbeddedList.size() + " users).");
        }
        System.out.println("Finished updating embedded user lists for all involved regions.");
    }
}
