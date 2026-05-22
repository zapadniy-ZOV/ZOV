package itmo.rshd.service;

import itmo.rshd.model.Region;
import itmo.rshd.model.Region.RegionType;
import itmo.rshd.model.User;
import itmo.rshd.model.User.SocialStatus;
import itmo.rshd.repository.reactive.ReactiveRegionRepository;
import itmo.rshd.repository.reactive.ReactiveUserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
                .flatMap(region -> {
                    Mono<Stats> directStats = reactiveUserRepository
                            .findByDistrictIdAndActiveTrue(regionId)
                            .reduce(Stats.empty(), Stats::withUser);

                    Mono<Stats> subStats = region.getType() == RegionType.DISTRICT
                            ? Mono.just(Stats.empty())
                            : reactiveRegionRepository.findByParentRegionId(regionId)
                                    .reduce(Stats.empty(), Stats::withSubRegion);

                    return Mono.zip(directStats, subStats, Stats::merge)
                            .flatMap(total -> {
                                region.setPopulationCount(total.population());
                                region.setAverageSocialRating(total.population() > 0
                                        ? total.ratingSum() / total.population()
                                        : 0);
                                region.setImportantPersonsCount(total.importantCount());
                                region.setUnderThreat(false);
                                return reactiveRegionRepository.save(region);
                            });
                });
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
                                .flatMap(city -> isAssigned(city.getParentRegionId())
                                        ? updateRegionStatistics(city.getParentRegionId()).then()
                                        : Mono.<Void>empty());
                    })
                    .then();
        } else if (isAssigned(user.getRegionId())) {
            String regionId = user.getRegionId();
            hierarchyUpdate = updateRegionStatistics(regionId)
                    .then(reactiveRegionRepository.findById(regionId))
                    .flatMap(region -> isAssigned(region.getParentRegionId())
                            ? updateRegionStatistics(region.getParentRegionId()).then()
                            : Mono.<Void>empty())
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

    private record Stats(int population, double ratingSum, int importantCount) {

        static Stats empty() {
            return new Stats(0, 0.0, 0);
        }

        Stats withUser(User u) {
            boolean important = u.getStatus() == SocialStatus.IMPORTANT || u.getStatus() == SocialStatus.VIP;
            return new Stats(population + 1, ratingSum + u.getSocialRating(), importantCount + (important ? 1 : 0));
        }

        Stats withSubRegion(Region r) {
            return new Stats(
                    population + r.getPopulationCount(),
                    ratingSum + r.getAverageSocialRating() * r.getPopulationCount(),
                    importantCount + r.getImportantPersonsCount());
        }

        static Stats merge(Stats a, Stats b) {
            return new Stats(a.population + b.population, a.ratingSum + b.ratingSum,
                    a.importantCount + b.importantCount);
        }
    }
}
