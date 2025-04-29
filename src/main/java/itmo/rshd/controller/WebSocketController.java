package itmo.rshd.controller;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.User;
import itmo.rshd.model.websocket.LocationUpdate;
import itmo.rshd.model.websocket.RatingUpdate;
import itmo.rshd.service.UserService;
import itmo.rshd.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;

@Controller
public class WebSocketController {

    private final UserService userService;
    private final WebSocketService webSocketService;
    
    @Autowired
    public WebSocketController(UserService userService, WebSocketService webSocketService) {
        this.userService = userService;
        this.webSocketService = webSocketService;
    }
    
    /**
     * Handle user connecting to WebSocket
     */
    @MessageMapping("/connect")
    public void handleConnect(SimpMessageHeaderAccessor headerAccessor, @Payload String userId) {
        // Store the user ID in the session for future use
        headerAccessor.getSessionAttributes().put("userId", userId);
        
        // Get user information
        Optional<User> user = userService.getUserById(userId);
        
        // If the user exists, notify about their successful connection
        if (user.isPresent()) {
            // Update last activity timestamp
            User updatedUser = user.get();
            updatedUser.setLastLocationUpdateTimestamp(System.currentTimeMillis());
            userService.updateUser(updatedUser);
            
            // Broadcast user's connection
            webSocketService.notifyUserLocationUpdate(updatedUser);
            
            // Send nearby users to the connected user
            List<User> nearbyUsers = userService.findUsersNearLocation(
                    updatedUser.getCurrentLocation(),
                    50.0 // Default 50km radius
            );
            webSocketService.notifyNearbyUsersUpdate(userId, nearbyUsers);
        }
    }
    
    /**
     * Handle location updates from users
     */
    @MessageMapping("/update-location")
    public void handleLocationUpdate(@Payload LocationUpdate locationUpdate) {
        String userId = locationUpdate.getUserId();
        GeoLocation location = locationUpdate.getLocation();
        
        Optional<User> userOpt = userService.getUserById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // Assuming these IDs are known (in a real app, you'd determine them based on the location)
            String regionId = user.getRegionId();
            String districtId = user.getDistrictId();
            String countryId = user.getCountryId();
            
            // Update user's location
            User updatedUser = userService.updateUserLocation(
                    userId, 
                    location, 
                    regionId, 
                    districtId, 
                    countryId
            );
            
            // Broadcast the updated user information
            webSocketService.notifyUserLocationUpdate(updatedUser);
            
            // Find nearby users and notify the user
            List<User> nearbyUsers = userService.findUsersNearLocation(location, 50.0);
            webSocketService.notifyNearbyUsersUpdate(userId, nearbyUsers);
            
            // Also notify nearby users about this user
            for (User nearbyUser : nearbyUsers) {
                if (!nearbyUser.getId().equals(userId)) {
                    List<User> usersNearNearbyUser = userService.findUsersNearLocation(
                            nearbyUser.getCurrentLocation(), 
                            50.0
                    );
                    webSocketService.notifyNearbyUsersUpdate(nearbyUser.getId(), usersNearNearbyUser);
                }
            }
        }
    }
    
    /**
     * Handle rating updates
     */
    @MessageMapping("/rate-person")
    public void handleRating(@Payload RatingUpdate ratingUpdate) {
        String userId = ratingUpdate.getUserId();           // Who is rating
        String targetUserId = ratingUpdate.getTargetUserId(); // Who is being rated
        double ratingChange = ratingUpdate.getRatingChange();  // The rating change
        
        // Update the target user's social rating
        User updatedTargetUser = userService.updateSocialRating(targetUserId, 
            ratingChange);
        
        // Update the rater's social rating based on their action
        User updatedRaterUser = userService.updateRaterSocialRating(userId, 
            targetUserId, ratingChange);
        
        // Notify all users about both updates
        if (updatedTargetUser != null) {
            webSocketService.notifyUserLocationUpdate(updatedTargetUser);
            webSocketService.notifySocialRatingChange(targetUserId, updatedTargetUser);
        }
        
        if (updatedRaterUser != null) {
            webSocketService.notifyUserLocationUpdate(updatedRaterUser);
            webSocketService.notifySocialRatingChange(userId, updatedRaterUser);
        }
    }
} 