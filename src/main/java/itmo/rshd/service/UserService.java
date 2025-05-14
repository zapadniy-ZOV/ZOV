package itmo.rshd.service;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.User;
import itmo.rshd.model.User.SocialStatus;
import itmo.rshd.repository.UserRepository;
import itmo.rshd.model.Region;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RegionService regionService;

    @Autowired
    public UserService(UserRepository userRepository, RegionService regionService) {
        this.userRepository = userRepository;
        this.regionService = regionService;
    }

    public User createUser(User user) {
        User savedUser = userRepository.save(user);

        if (savedUser.getDistrictId() != null && !savedUser.getDistrictId().equals("none")) {
            regionService.getRegionById(savedUser.getDistrictId()).ifPresent(region -> {
                region.getUsers().add(savedUser);
                regionService.updateRegion(region);
            });
        }
        return savedUser;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User updateUser(User user) {
        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User userToDelete = userOpt.get();
            String districtId = userToDelete.getDistrictId();

            userRepository.deleteById(id);

            if (districtId != null && !districtId.equals("none")) {
                regionService.getRegionById(districtId).ifPresent(oldRegion -> {
                    boolean removed = oldRegion.getUsers().removeIf(u -> u.getId().equals(id));
                    if (removed) {
                        regionService.updateRegion(oldRegion);
                        regionService.updateRegionStatistics(districtId);
                    }
                });
            }
        }
    }

    public User updateUserLocation(String userId, GeoLocation location, String regionId, String districtId, String countryId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String oldDistrictId = user.getDistrictId();

            user.setCurrentLocation(location);
            user.setRegionId(regionId);
            user.setDistrictId(districtId);
            user.setCountryId(countryId);
            user.setLastLocationUpdateTimestamp(System.currentTimeMillis());
            User updatedUser = userRepository.save(user);

            if (oldDistrictId != null && !oldDistrictId.equals("none") && !oldDistrictId.equals(updatedUser.getDistrictId())) {
                regionService.getRegionById(oldDistrictId).ifPresent(oldRegion -> {
                    boolean removed = oldRegion.getUsers().removeIf(u -> u.getId().equals(userId));
                    if (removed) {
                        regionService.updateRegion(oldRegion);
                        regionService.updateRegionStatistics(oldDistrictId);
                    }
                });
            }

            if (updatedUser.getDistrictId() != null && !updatedUser.getDistrictId().equals("none")) {
                final String newEffectiveDistrictId = updatedUser.getDistrictId();
                regionService.getRegionById(newEffectiveDistrictId).ifPresent(newRegion -> {
                    newRegion.getUsers().removeIf(u -> u.getId().equals(userId));
                    newRegion.getUsers().add(updatedUser);
                    regionService.updateRegion(newRegion);
                });
            }
            
            updateUserRelatedRegionStatistics(updatedUser);
            return updatedUser;
        }
        return null;
    }

    public User updateSocialRating(String userId, double newRating) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setSocialRating(newRating);
            
            // Update user status based on rating
            if (newRating >= 900) {
                user.setStatus(SocialStatus.VIP);
            } else if (newRating >= 700) {
                user.setStatus(SocialStatus.IMPORTANT);
            } else if (newRating >= 400) {
                user.setStatus(SocialStatus.REGULAR);
            } else {
                user.setStatus(SocialStatus.LOW);
            }
            
            User updatedUser = userRepository.save(user);
            
            // Update user in the region's embedded list
            if (updatedUser.getDistrictId() != null && !updatedUser.getDistrictId().equals("none")) {
                String districtId = updatedUser.getDistrictId();
                regionService.getRegionById(districtId).ifPresent(region -> {
                    region.getUsers().removeIf(u -> u.getId().equals(userId));
                    region.getUsers().add(updatedUser);
                    regionService.updateRegion(region);
                });
            }
            
            updateUserRelatedRegionStatistics(updatedUser);
            return updatedUser;
        }
        return null;
    }

    public List<User> findUsersInRegion(String regionId) {
        Optional<Region> regionOpt = regionService.getRegionById(regionId);
        return regionOpt.map(Region::getUsers).orElseGet(Collections::emptyList);
    }

    public List<User> findImportantPersonsInRegion(String regionId) {
        Optional<Region> regionOpt = regionService.getRegionById(regionId);
        if (regionOpt.isPresent()) {
            Region region = regionOpt.get();
            if (region.getUsers() != null) {
                return region.getUsers().stream()
                        .filter(user -> user.getStatus() == SocialStatus.IMPORTANT || user.getStatus() == SocialStatus.VIP)
                        .collect(java.util.stream.Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    public List<User> findUsersNearLocation(GeoLocation location, double maxDistanceKm) {
        // Convert km to meters for MongoDB query
        double maxDistanceMeters = maxDistanceKm * 1000;
        
        // Use the new geospatial query method
        return userRepository.findByCurrentLocationNear(
            location.getLatitude(),
            location.getLongitude(),
            maxDistanceMeters
        );
    }

    public List<User> findUsersBelowRating(double threshold) {
        return userRepository.findUsersBelowRating(threshold);
    }

    /**
     * Update user's social rating based on their rating of another user
     * @param raterId ID of the user giving the rating
     * @param targetId ID of the user being rated
     * @param ratingChange The rating change (positive for like, negative for dislike)
     * @return The updated rater user
     */
    public User updateRaterSocialRating(String raterId, String targetId, double ratingChange) {
        Optional<User> raterOpt = userRepository.findById(raterId);
        Optional<User> targetOpt = userRepository.findById(targetId);
        
        if (raterOpt.isPresent() && targetOpt.isPresent()) {
            User rater = raterOpt.get();
            User target = targetOpt.get();

            // If rater is VIP or IMPORTANT, they affect the target's rating
            if (rater.getStatus() == SocialStatus.VIP || rater.getStatus() == SocialStatus.IMPORTANT) {
                double multiplier = rater.getStatus() == SocialStatus.VIP ? 2.0 : 1.5;
                double baseImpact = ratingChange > 0 ? 0.5 : -0.5; // Base impact for like/dislike
                double impact = baseImpact * multiplier;
                
                double newTargetRating = target.getSocialRating() + impact;
                newTargetRating = Math.max(0, Math.min(100, newTargetRating)); // Clamp to 0-100
                
                target.setSocialRating(newTargetRating);
                updateUserStatusBasedOnRating(target); // Update target's status
                User updatedTarget = userRepository.save(target);
                
                // Update updatedTarget in its region's embedded list
                if (updatedTarget.getDistrictId() != null && !updatedTarget.getDistrictId().equals("none")) {
                    String districtId = updatedTarget.getDistrictId();
                    regionService.getRegionById(districtId).ifPresent(region -> {
                        region.getUsers().removeIf(u -> u.getId().equals(targetId));
                        region.getUsers().add(updatedTarget);
                        regionService.updateRegion(region);
                    });
                }
                updateUserRelatedRegionStatistics(updatedTarget);
                return rater; // VIP/IMPORTANT Rater's rating doesn't change from this action, return original rater.
            } else {
                // Rater is Regular or Low, their own rating changes based on target's status
                double raterImpact = 0;
                switch (target.getStatus()) {
                    case VIP: raterImpact = ratingChange > 0 ? 5.0 : -10.0; break;
                    case IMPORTANT: raterImpact = ratingChange > 0 ? 3.0 : -7.0; break;
                    case REGULAR: raterImpact = ratingChange > 0 ? 1.0 : -3.0; break;
                    case LOW: raterImpact = ratingChange > 0 ? 0.5 : -1.0; break;
                }
                
                double newRaterRating = rater.getSocialRating() + raterImpact;
                newRaterRating = Math.max(0, Math.min(100, newRaterRating)); // Clamp to 0-100
                
                rater.setSocialRating(newRaterRating);
                updateUserStatusBasedOnRating(rater); // Update rater's status
                User updatedRater = userRepository.save(rater);
                
                // Update updatedRater in its region's embedded list
                if (updatedRater.getDistrictId() != null && !updatedRater.getDistrictId().equals("none")) {
                    String districtId = updatedRater.getDistrictId();
                    regionService.getRegionById(districtId).ifPresent(region -> {
                        region.getUsers().removeIf(u -> u.getId().equals(raterId));
                        region.getUsers().add(updatedRater);
                        regionService.updateRegion(region);
                    });
                }
                updateUserRelatedRegionStatistics(updatedRater);
                return updatedRater; // Return the updated rater
            }
        }
        return null;
    }

    // Helper method to update status based on rating
    private void updateUserStatusBasedOnRating(User user) {
        double rating = user.getSocialRating();
        if (rating >= 90) {
            user.setStatus(SocialStatus.VIP);
        } else if (rating >= 70) {
            user.setStatus(SocialStatus.IMPORTANT);
        } else if (rating >= 40) {
            user.setStatus(SocialStatus.REGULAR);
        } else {
            user.setStatus(SocialStatus.LOW);
        }
    }

    // Helper method to update region statistics for a user's regions
    private void updateUserRelatedRegionStatistics(User user) {
        // Update district, city, region, and country statistics
        if (user.getDistrictId() != null && !user.getDistrictId().equals("none")) {
            regionService.updateRegionStatistics(user.getDistrictId());
            
            // Find parent city of this district
            Optional<Region> districtOpt = regionService.getRegionById(user.getDistrictId());
            if (districtOpt.isPresent() && districtOpt.get().getParentRegionId() != null) {
                String cityId = districtOpt.get().getParentRegionId();
                regionService.updateRegionStatistics(cityId);
                
                // Find parent region of this city
                Optional<Region> cityOpt = regionService.getRegionById(cityId);
                if (cityOpt.isPresent() && cityOpt.get().getParentRegionId() != null) {
                    String regionId = cityOpt.get().getParentRegionId();
                    regionService.updateRegionStatistics(regionId);
                }
            }
        } else if (user.getRegionId() != null && !user.getRegionId().equals("none")) {
            // User might be directly associated with a city or region
            regionService.updateRegionStatistics(user.getRegionId());
            
            // Check if this is a city and update its parent region
            Optional<Region> regionOpt = regionService.getRegionById(user.getRegionId());
            if (regionOpt.isPresent() && regionOpt.get().getParentRegionId() != null) {
                regionService.updateRegionStatistics(regionOpt.get().getParentRegionId());
            }
        }
        
        // Always update the country statistics
        if (user.getCountryId() != null) {
            regionService.updateRegionStatistics(user.getCountryId());
        }
    }
} 