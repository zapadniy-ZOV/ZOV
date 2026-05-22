package itmo.rshd.repository.reactive;

import itmo.rshd.model.Region;
import itmo.rshd.model.Region.RegionType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ReactiveRegionRepository extends ReactiveMongoRepository<Region, String> {
    Flux<Region> findByParentRegionId(String parentRegionId);

    Flux<Region> findByType(RegionType type);
}
