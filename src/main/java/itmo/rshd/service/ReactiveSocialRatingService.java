package itmo.rshd.service;

import itmo.rshd.model.User.SocialStatus;
import itmo.rshd.model.User;
import itmo.rshd.repository.reactive.ReactiveUserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReactiveSocialRatingService {
    private final ReactiveUserRepository reactiveUserRepository;
    private final ReactiveRegionRatingService reactiveRegionRatingService;

    public ReactiveSocialRatingService(ReactiveUserRepository reactiveUserRepository,
            ReactiveRegionRatingService reactiveRegionRatingService) {
        this.reactiveUserRepository = reactiveUserRepository;
        this.reactiveRegionRatingService = reactiveRegionRatingService;
    }

    public Mono<User> updateTargetSocialRating(String raterId, String targetId, double ratingChange) {
        return Mono.zip(
                reactiveUserRepository.findById(raterId),
                reactiveUserRepository.findById(targetId))
                .flatMap(tuple -> {
                    User rater = tuple.getT1();
                    User target = tuple.getT2();

                    double ratingAction = ratingChange > 0 ? 1.0 : -1.0;
                    int raterStatusWeight = getStatusWeight(rater.getStatus());
                    int targetStatusWeight = getStatusWeight(target.getStatus());
                    double statusMultiplier = (double) raterStatusWeight / Math.max(1, targetStatusWeight);

                    double raterActualRating = Math.max(0.0, rater.getSocialRating());
                    double targetSocialRatingForCalc = Math.max(1.0, target.getSocialRating());
                    double ratingRatio = raterActualRating / targetSocialRatingForCalc;
                    double ratingRatioMultiplier = Math.min(ratingRatio * 0.2, 2.5);
                    double overallMultiplier = statusMultiplier * ratingRatioMultiplier;
                    double impact = ratingAction * overallMultiplier;

                    double newTargetRating = target.getSocialRating() + impact;
                    newTargetRating = Math.max(0, Math.min(100, newTargetRating));
                    target.setSocialRating(newTargetRating);
                    updateUserStatusBasedOnRating(target);

                    return reactiveUserRepository.save(target);
                })
                .flatMap(savedTarget -> reactiveRegionRatingService.updateUserRelatedRegionStatistics(savedTarget)
                        .thenReturn(savedTarget));
    }

    public Mono<User> updateSocialRating(String userId, double newRating) {
        return reactiveUserRepository.findById(userId)
                .flatMap(user -> {
                    user.setSocialRating(Math.max(0, Math.min(100, newRating)));
                    updateUserStatusBasedOnRating(user);
                    return reactiveUserRepository.save(user);
                })
                .flatMap(savedUser -> reactiveRegionRatingService.updateUserRelatedRegionStatistics(savedUser)
                        .thenReturn(savedUser));
    }

    private int getStatusWeight(SocialStatus status) {
        if (status == null) {
            return 1;
        }
        return switch (status) {
            case VIP -> 4;
            case IMPORTANT -> 3;
            case REGULAR -> 2;
            case LOW -> 1;
        };
    }

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
}
