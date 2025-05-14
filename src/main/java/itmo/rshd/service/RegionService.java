package itmo.rshd.service;

import itmo.rshd.model.GeoLocation;
import itmo.rshd.model.Region;
import itmo.rshd.model.Region.RegionType;
import itmo.rshd.model.User;
import itmo.rshd.repository.RegionRepository;
import itmo.rshd.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
            @Lazy RegionAssessmentService regionAssessmentService) {
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
            List<User> directUsers = region.getUsers(); // Get embedded users

            int populationFromDirectUsers = 0;
            double ratingSumFromDirectUsers = 0;
            int importantFromDirectUsers = 0;

            if (directUsers != null) {
                for (User user : directUsers) {
                    if (user.isActive()) {
                        populationFromDirectUsers++;
                        ratingSumFromDirectUsers += user.getSocialRating();
                        if (user.getStatus() == User.SocialStatus.IMPORTANT || user.getStatus() == User.SocialStatus.VIP) {
                            importantFromDirectUsers++;
                        }
                    }
                }
            }

            int totalPopulation = populationFromDirectUsers;
            double totalWeightedRatingSum = ratingSumFromDirectUsers;
            int totalImportantPersons = importantFromDirectUsers;

            if (region.getType() != Region.RegionType.DISTRICT) {
                List<Region> subRegions = regionRepository.findByParentRegionId(region.getId());
                for (Region subRegion : subRegions) {
                    totalPopulation += subRegion.getPopulationCount();
                    totalWeightedRatingSum += subRegion.getAverageSocialRating() * subRegion.getPopulationCount();
                    totalImportantPersons += subRegion.getImportantPersonsCount();
                }
            }

            region.setPopulationCount(totalPopulation);

            if (totalPopulation > 0) {
                region.setAverageSocialRating(totalWeightedRatingSum / totalPopulation);
                region.setImportantPersonsCount(totalImportantPersons);

                if (region.getType() != Region.RegionType.COUNTRY) {
                    boolean underThreat = regionAssessmentService.shouldDeployOreshnik(region.getId());
                    region.setUnderThreat(underThreat);
                } else {
                    region.setUnderThreat(false);
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