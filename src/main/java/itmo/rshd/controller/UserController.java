package itmo.rshd.controller;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.User;
import itmo.rshd.service.UserService;
import itmo.rshd.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final WebSocketService webSocketService;

    @Autowired
    public UserController(UserService userService, WebSocketService webSocketService) {
        this.userService = userService;
        this.webSocketService = webSocketService;
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestParam String username, @RequestParam String password) {
        User user = userService.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            return new ResponseEntity<>(user, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User createdUser = userService.createUser(user);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        Optional<User> user = userService.getUserById(id);
        return user.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User user) {
        Optional<User> existingUser = userService.getUserById(id);
        if (existingUser.isPresent()) {
            user.setId(id);
            User updatedUser = userService.updateUser(user);

            // Notify all subscribers about the update
            webSocketService.notifyUserLocationUpdate(updatedUser);

            return new ResponseEntity<>(updatedUser, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        Optional<User> existingUser = userService.getUserById(id);
        if (existingUser.isPresent()) {
            userService.deleteUser(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/location")
    public ResponseEntity<User> updateUserLocation(
            @PathVariable String id,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam String regionId,
            @RequestParam String districtId,
            @RequestParam String countryId) {

        GeoLocation location = new GeoLocation(latitude, longitude);
        User updatedUser = userService.updateUserLocation(id, location, regionId, districtId, countryId);

        if (updatedUser != null) {
            // Notify via WebSocket about user location change
            webSocketService.notifyUserLocationUpdate(updatedUser);

            // Find nearby users and notify this user
            List<User> nearbyUsers = userService.findUsersNearLocation(location, 50.0);
            webSocketService.notifyNearbyUsersUpdate(id, nearbyUsers);

            // Also notify nearby users about this user
            for (User nearbyUser : nearbyUsers) {
                if (!nearbyUser.getId().equals(id)) {
                    List<User> usersNearOtherUser = userService.findUsersNearLocation(
                            nearbyUser.getCurrentLocation(),
                            50.0);
                    webSocketService.notifyNearbyUsersUpdate(nearbyUser.getId(), usersNearOtherUser);
                }
            }

            return new ResponseEntity<>(updatedUser, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/social-rating")
    public ResponseEntity<User> updateSocialRating(
            @PathVariable String id,
            @RequestParam double rating,
            @RequestParam(required = false) String raterId) {

        User updatedUser = userService.updateSocialRating(id, rating);

        // If this rating was given by another user, update their rating too
        if (raterId != null && !raterId.isEmpty()) {
            userService.updateRaterSocialRating(raterId, id, rating > 0 ? 1.0 : -1.0);
        }

        if (updatedUser != null) {
            // Notify via WebSocket about user rating change
            webSocketService.notifyUserLocationUpdate(updatedUser);
            webSocketService.notifySocialRatingChange(id, updatedUser);

            return new ResponseEntity<>(updatedUser, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/region/{regionId}")
    public ResponseEntity<List<User>> getUsersInRegion(@PathVariable String regionId) {
        List<User> users = userService.findUsersInRegion(regionId);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @GetMapping("/region/{regionId}/important")
    public ResponseEntity<List<User>> getImportantPersonsInRegion(@PathVariable String regionId) {
        List<User> users = userService.findImportantPersonsInRegion(regionId);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @GetMapping("/near")
    public ResponseEntity<List<User>> getUsersNearLocation(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double maxDistanceKm) {

        GeoLocation location = new GeoLocation(latitude, longitude);
        List<User> users = userService.findUsersNearLocation(location, maxDistanceKm);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @GetMapping("/below-rating/{threshold}")
    public ResponseEntity<List<User>> getUsersBelowRating(@PathVariable double threshold) {
        List<User> users = userService.findUsersBelowRating(threshold);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @SuppressWarnings("unused")
    @GetMapping("/test-users")
    public ResponseEntity<List<Object>> getTestUserCredentials() {
        List<User> users = userService.getAllUsers();
        List<Object> credentials = users.stream()
                .map(user -> new Object() {
                    public final String username = user.getUsername();
                    public final String password = user.getPassword();
                    public final String fullName = user.getFullName();
                    public final User.SocialStatus status = user.getStatus();
                })
                .limit(20) // Limit to first 20 users
                .collect(java.util.stream.Collectors.toList());
        return new ResponseEntity<>(credentials, HttpStatus.OK);
    }
}