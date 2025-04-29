package itmo.rshd.service;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.Region;
import itmo.rshd.model.Region.RegionType;
import itmo.rshd.model.User;
import itmo.rshd.repository.RegionRepository;
import itmo.rshd.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RegionService {

    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final RegionAssessmentService regionAssessmentService;

    @Autowired
    public RegionService(RegionRepository regionRepository, UserRepository userRepository,
            RegionAssessmentService regionAssessmentService) {
        this.regionRepository = regionRepository;
        this.userRepository = userRepository;
        this.regionAssessmentService = regionAssessmentService;
    }

    public Region createRegion(Region region) {
        return regionRepository.save(region);
    }

    public List<Region> getAllRegions() {
        return regionRepository.findAll();
    }

    public Optional<Region> getRegionById(String id) {
        return regionRepository.findById(id);
    }

    public Region updateRegion(Region region) {
        return regionRepository.save(region);
    }

    public void deleteRegion(String id) {
        regionRepository.deleteById(id);
    }

    public List<Region> findRegionsByType(RegionType type) {
        return regionRepository.findByType(type);
    }

    public List<Region> findSubRegions(String parentRegionId) {
        return regionRepository.findByParentRegionId(parentRegionId);
    }

    public List<Region> findRegionsContainingPoint(GeoLocation location) {
        return regionRepository.findRegionsContainingPoint(location.getLongitude(), location.getLatitude());
    }

    public List<Region> findLowRatedRegionsWithoutImportantPersons(double threshold) {
        return regionRepository.findLowRatedRegionsWithoutImportantPersons(threshold);
    }

    public Region updateRegionStatistics(String regionId) {
        Optional<Region> regionOpt = regionRepository.findById(regionId);
        if (regionOpt.isPresent()) {
            Region region = regionOpt.get();

            // Initialize counters
            int activePopulation = 0;
            double totalRating = 0;
            int importantCount = 0;

            if (region.getType() == Region.RegionType.COUNTRY) {
                // For country, we need to count all active users
                List<User> allActiveUsers = userRepository.findByActive(true);
                activePopulation = allActiveUsers.size();

                if (activePopulation > 0) {
                    for (User user : allActiveUsers) {
                        totalRating += user.getSocialRating();
                        if (user.getStatus() == User.SocialStatus.IMPORTANT ||
                                user.getStatus() == User.SocialStatus.VIP) {
                            importantCount++;
                        }
                    }
                }
            } else if (region.getType() == Region.RegionType.REGION) {
                // For region, count all users in the region directly plus users in all cities
                // and districts
                List<User> directUsers = userRepository.findByRegionId(regionId);
                activePopulation = directUsers.size();

                // Add ratings and important counts for direct users
                for (User user : directUsers) {
                    totalRating += user.getSocialRating();
                    if (user.getStatus() == User.SocialStatus.IMPORTANT ||
                            user.getStatus() == User.SocialStatus.VIP) {
                        importantCount++;
                    }
                }

                // Count users in cities within this region
                List<Region> citiesInRegion = regionRepository.findByParentRegionId(regionId);
                for (Region city : citiesInRegion) {
                    // Find all districts in this city
                    List<Region> districtsInCity = regionRepository.findByParentRegionId(city.getId());

                    // Count users in the city directly
                    List<User> cityUsers = userRepository.findByRegionId(city.getId());
                    activePopulation += cityUsers.size();

                    for (User user : cityUsers) {
                        totalRating += user.getSocialRating();
                        if (user.getStatus() == User.SocialStatus.IMPORTANT ||
                                user.getStatus() == User.SocialStatus.VIP) {
                            importantCount++;
                        }
                    }

                    // Count users in all districts of this city
                    for (Region district : districtsInCity) {
                        List<User> districtUsers = userRepository.findByDistrictId(district.getId());
                        activePopulation += districtUsers.size();

                        for (User user : districtUsers) {
                            totalRating += user.getSocialRating();
                            if (user.getStatus() == User.SocialStatus.IMPORTANT ||
                                    user.getStatus() == User.SocialStatus.VIP) {
                                importantCount++;
                            }
                        }
                    }
                }
            } else if (region.getType() == Region.RegionType.CITY) {
                // For cities, we need to count both officials directly associated with the city
                // AND users in all districts belonging to the city

                // Find all districts in this city
                List<Region> districtsInCity = regionRepository.findByParentRegionId(region.getId());
                List<String> districtIds = districtsInCity.stream()
                        .map(Region::getId)
                        .collect(java.util.stream.Collectors.toList());

                // Get all users in the city including those in its districts
                List<User> allCityUsers = userRepository.findByCityIdIncludingDistricts(
                        region.getId(), districtIds);

                activePopulation = allCityUsers.size();

                // Calculate ratings and count important persons
                if (activePopulation > 0) {
                    totalRating = allCityUsers.stream()
                            .mapToDouble(User::getSocialRating)
                            .sum();

                    importantCount = (int) allCityUsers.stream()
                            .filter(u -> u.getStatus() == User.SocialStatus.IMPORTANT ||
                                    u.getStatus() == User.SocialStatus.VIP)
                            .count();
                }
            } else if (region.getType() == Region.RegionType.DISTRICT) {
                // For districts, just count direct users
                List<User> districtUsers = userRepository.findByDistrictId(regionId);
                activePopulation = districtUsers.size();

                for (User user : districtUsers) {
                    totalRating += user.getSocialRating();
                    if (user.getStatus() == User.SocialStatus.IMPORTANT ||
                            user.getStatus() == User.SocialStatus.VIP) {
                        importantCount++;
                    }
                }
            }

            // Update region statistics
            region.setPopulationCount(activePopulation);

            if (activePopulation > 0) {
                region.setAverageSocialRating(totalRating / activePopulation);
                region.setImportantPersonsCount(importantCount);

                // Only check for threat if it's not a country
                if (region.getType() != Region.RegionType.COUNTRY) {
                    boolean underThreat = regionAssessmentService.shouldDeployOreshnik(region.getId());
                    region.setUnderThreat(underThreat);
                } else {
                    region.setUnderThreat(false); // Country is never under direct threat
                }
            } else {
                region.setAverageSocialRating(0);
                region.setImportantPersonsCount(0);
                region.setUnderThreat(false);
            }

            return regionRepository.save(region);
        }
        return null;
    }

    public List<Region> updateAllRegionsStatistics() {
        List<Region> allRegions = regionRepository.findAll();
        List<Region> updatedRegions = new java.util.ArrayList<>();

        for (Region region : allRegions) {
            Region updatedRegion = updateRegionStatistics(region.getId());
            if (updatedRegion != null) {
                updatedRegions.add(updatedRegion);
            }
        }

        return updatedRegions;
    }

    public List<Region> findRegionsUnderThreat(RegionType type) {
        return regionRepository.findRegionsUnderThreat(type);
    }

    public List<Region> getRegionsByType(RegionType type) {
        return regionRepository.findByType(type);
    }
}