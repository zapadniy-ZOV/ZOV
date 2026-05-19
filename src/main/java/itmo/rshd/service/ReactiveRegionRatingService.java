package itmo.rshd.service;

import itmo.rshd.model.Region.RegionType;
import itmo.rshd.model.User.SocialStatus;
import itmo.rshd.model.Region;
import itmo.rshd.model.User;
import itmo.rshd.repository.reactive.ReactiveRegionRepository;
import itmo.rshd.repository.reactive.ReactiveUserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
public class ReactiveRegionRatingService {
    private final ReactiveRegionRepository reactiveRegionRepository;
    private final ReactiveUserRepository reactiveUserRepository;

    public ReactiveRegionRatingService(ReactiveRegionRepository reactiveRegionRepository,
            ReactiveUserRepository reactiveUserRepository) {
        this.reactiveRegionRepository = reactiveRegionRepository;
        this.reactiveUserRepository = reactiveUserRepository;
    }

    public Mono<Region> updateRegionStatistics(String regionId) {
        return reactiveRegionRepository.findById(regionId)
                .flatMap(region -> Mono.zip(
                        reactiveUserRepository.findByDistrictIdAndActiveTrue(regionId).collectList(),
                        region.getType() == RegionType.DISTRICT
                                ? Mono.just(Collections.<Region>emptyList())
                                : reactiveRegionRepository.findByParentRegionId(regionId).collectList())
                        .flatMap(tuple -> {
                            List<User> directUsers = tuple.getT1();
                            List<Region> subRegions = tuple.getT2();

                            int populationFromDirectUsers = 0;
                            double ratingSumFromDirectUsers = 0;
                            int importantFromDirectUsers = 0;

                            for (User user : directUsers) {
                                populationFromDirectUsers++;
                                ratingSumFromDirectUsers += user.getSocialRating();
                                if (user.getStatus() == SocialStatus.IMPORTANT || user.getStatus() == SocialStatus.VIP) {
                                    importantFromDirectUsers++;
                                }
                            }

                            int totalPopulation = populationFromDirectUsers;
                            double totalWeightedRatingSum = ratingSumFromDirectUsers;
                            int totalImportantPersons = importantFromDirectUsers;

                            for (Region subRegion : subRegions) {
                                totalPopulation += subRegion.getPopulationCount();
                                totalWeightedRatingSum += subRegion.getAverageSocialRating()
                                        * subRegion.getPopulationCount();
                                totalImportantPersons += subRegion.getImportantPersonsCount();
                            }

                            region.setPopulationCount(totalPopulation);
                            if (totalPopulation > 0) {
                                region.setAverageSocialRating(totalWeightedRatingSum / totalPopulation);
                                region.setImportantPersonsCount(totalImportantPersons);
                                region.setUnderThreat(false);
                            } else {
                                region.setAverageSocialRating(0);
                                region.setImportantPersonsCount(0);
                                region.setUnderThreat(false);
                            }

                            return reactiveRegionRepository.save(region);
                        }));
    }

    public Mono<Void> updateUserRelatedRegionStatistics(User user) {
        Mono<Void> hierarchyUpdate;
        if (isAssigned(user.getDistrictId())) {
            String districtId = user.getDistrictId();
            hierarchyUpdate = updateRegionStatistics(districtId)
                    .then(reactiveRegionRepository.findById(districtId))
                    .flatMap(district -> {
                        if (!isAssigned(district.getParentRegionId())) {
                            return Mono.<Void>empty();
                        }

                        String cityId = district.getParentRegionId();
                        return updateRegionStatistics(cityId)
                                .then(reactiveRegionRepository.findById(cityId))
                                .flatMap(city -> {
                                    if (!isAssigned(city.getParentRegionId())) {
                                        return Mono.<Void>empty();
                                    }
                                    return updateRegionStatistics(city.getParentRegionId()).then();
                                });
                    })
                    .then();
        } else if (isAssigned(user.getRegionId())) {
            String regionId = user.getRegionId();
            hierarchyUpdate = updateRegionStatistics(regionId)
                    .then(reactiveRegionRepository.findById(regionId))
                    .flatMap(region -> isAssigned(region.getParentRegionId())
                            ? updateRegionStatistics(region.getParentRegionId()).then()
                            : Mono.empty())
                    .then();
        } else {
            hierarchyUpdate = Mono.empty();
        }

        Mono<Void> countryUpdate = isAssigned(user.getCountryId())
                ? updateRegionStatistics(user.getCountryId()).then()
                : Mono.empty();

        return hierarchyUpdate.then(countryUpdate);
    }

    private boolean isAssigned(String regionId) {
        return regionId != null && !regionId.isBlank() && !"none".equalsIgnoreCase(regionId);
    }
}
