package itmo.rshd.service;

import itmo.rshd.model.Region;
import itmo.rshd.model.User;
import itmo.rshd.model.websocket.MissileLaunch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Send user location update to all subscribers
     */
    public void notifyUserLocationUpdate(User user) {
        messagingTemplate.convertAndSend("/topic/user-location-update", user);
    }
    
    /**
     * Send nearby users update to a specific user
     */
    public void notifyNearbyUsersUpdate(String userId, List<User> nearbyUsers) {
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/users-nearby-update",
                nearbyUsers
        );
    }
    
    /**
     * Broadcast region status update to all subscribers
     */
    public void notifyRegionStatusUpdate(Region region) {
        messagingTemplate.convertAndSend("/topic/region-status-update", region);
    }
    
    /**
     * Broadcast missile launch event to all subscribers
     */
    public void notifyMissileLaunch(String regionId, String missileType) {
        MissileLaunch missileLaunch = new MissileLaunch();
        missileLaunch.setRegionId(regionId);
        missileLaunch.setMissileType(missileType);
        
        messagingTemplate.convertAndSend("/topic/missile-launch", missileLaunch);
    }
    
    /**
     * Notify a specific user about their social rating change
     */
    public void notifySocialRatingChange(String userId, User updatedUser) {
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/social-rating-update",
                updatedUser
        );
    }
} 