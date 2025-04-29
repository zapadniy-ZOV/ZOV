package itmo.rshd.service;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.User;
import itmo.rshd.model.User.SocialStatus;
import itmo.rshd.repository.UserRepository;
import itmo.rshd.model.Region;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        return userRepository.save(user);
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
        userRepository.deleteById(id);
    }

    public User updateUserLocation(String userId, GeoLocation location, String regionId, String districtId, String countryId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setCurrentLocation(location);
            user.setRegionId(regionId);
            user.setDistrictId(districtId);
            user.setCountryId(countryId);
            user.setLastLocationUpdateTimestamp(System.currentTimeMillis());
            return userRepository.save(user);
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
            
            // Update region statistics for all affected regions
            updateUserRelatedRegionStatistics(user);
            
            return updatedUser;
        }
        return null;
    }

    public List<User> findUsersInRegion(String regionId) {
        return userRepository.findByRegionId(regionId);
    }

    public List<User> findImportantPersonsInRegion(String regionId) {
        return userRepository.findImportantPersonsInRegion(regionId);
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
            double raterImpact = 0;
            
            // If rater is VIP or IMPORTANT, they have special rating power
            if (rater.getStatus() == SocialStatus.VIP || rater.getStatus() == SocialStatus.IMPORTANT) {
                double multiplier = rater.getStatus() == SocialStatus.VIP ? 2.0 : 1.5;
                double baseImpact = ratingChange > 0 ? 0.5 : -0.5;
                double impact = baseImpact * multiplier;
                
                // Calculate new rating by applying the impact
                double newRating = target.getSocialRating() + impact;
                
                // Ensure rating stays within valid bounds (0-100)
                newRating = Math.max(0, Math.min(100, newRating));
                
                target.setSocialRating(newRating);
                // Update target's status based on new rating
                updateUserStatusBasedOnRating(target);
                User updatedTarget = userRepository.save(target);
                
                // Update region statistics for the target's regions
                updateUserRelatedRegionStatistics(target);
                
                return updatedTarget;
            }
            
            // For regular and low status users, use the original impact calculation
            switch (target.getStatus()) {
                case VIP:
                    raterImpact = ratingChange > 0 ? 5.0 : -10.0;
                    break;
                case IMPORTANT:
                    raterImpact = ratingChange > 0 ? 3.0 : -7.0;
                    break;
                case REGULAR:
                    raterImpact = ratingChange > 0 ? 1.0 : -3.0;
                    break;
                case LOW:
                    raterImpact = ratingChange > 0 ? 0.5 : -1.0;
                    break;
            }
            
            // Calculate new rating by adding the impact to current rating
            double newRating = rater.getSocialRating() + raterImpact;
            
            // Ensure rating stays within valid bounds (0-100)
            newRating = Math.max(0, Math.min(100, newRating));
            
            // Update rater's social rating
            rater.setSocialRating(newRating);
            
            // Update status based on new rating
            updateUserStatusBasedOnRating(rater);
            
            User updatedRater = userRepository.save(rater);
            
            // Update region statistics for the rater's regions
            updateUserRelatedRegionStatistics(rater);
            
            return updatedRater;
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