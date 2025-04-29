package itmo.rshd.repository;

import itmo.rshd.model.User;
import itmo.rshd.model.User.SocialStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    User findByUsername(String username);
    
    @Query("{'regionId': ?0, 'active': true}")
    List<User> findByRegionId(String regionId);
    
    @Query("{'$or': [{'regionId': ?0, 'districtId': 'none'}, {'districtId': {$in: ?1}}], 'active': true}")
    List<User> findByCityIdIncludingDistricts(String cityId, List<String> districtIds);
    
    @Query("{'districtId': ?0, 'active': true}")
    List<User> findByDistrictId(String districtId);
    
    @Query("{'countryId': ?0, 'active': true}")
    List<User> findByCountryId(String countryId);
    
    @Query("{'active': ?0}")
    List<User> findByActive(boolean active);
    
    List<User> findByStatus(SocialStatus status);
    
    // Using MongoDB's $geoNear operator for geospatial queries
    @Query("{'currentLocation.position': {$near: {$geometry: {type: 'Point', coordinates: [?1, ?0]}, $maxDistance: ?2}}, 'active': true}")
    List<User> findByCurrentLocationNear(double latitude, double longitude, double maxDistance);
    
    // For compatibility with existing service methods
    @Query("{'currentLocation.position': {$near: {$geometry: {type: 'Point', coordinates: [?0, ?1]}, $maxDistance: ?2}}, 'active': true}")
    List<User> findUsersNearLocation(double longitude, double latitude, double maxDistanceMeters);
    
    @Query("{'regionId': ?0, 'status': {$in: ['IMPORTANT', 'VIP']}, 'active': true}")
    List<User> findImportantPersonsInRegion(String regionId);
    
    @Query("{'socialRating': {$lt: ?0}, 'active': true}")
    List<User> findUsersBelowRating(double rating);
}
