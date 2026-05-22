package itmo.rshd.controller;

import itmo.rshd.model.User;
import itmo.rshd.repository.reactive.ReactiveRegionRepository;
import itmo.rshd.service.ReactiveSocialRatingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ReactiveRatingController {
    private final ReactiveSocialRatingService reactiveSocialRatingService;
    private final ReactiveRegionRepository reactiveRegionRepository;

    public ReactiveRatingController(ReactiveSocialRatingService reactiveSocialRatingService,
            ReactiveRegionRepository reactiveRegionRepository) {
        this.reactiveSocialRatingService = reactiveSocialRatingService;
        this.reactiveRegionRepository = reactiveRegionRepository;
    }

    @PutMapping("/users/{id}/social-rating")
    public Mono<ResponseEntity<User>> updateSocialRating(
            @PathVariable String id,
            @RequestParam("rating") double ratingValue,
            @RequestParam(required = false) String raterId) {

        Mono<User> processedUser = (raterId != null && !raterId.isBlank())
                ? reactiveSocialRatingService.updateTargetSocialRating(raterId, id, ratingValue)
                : reactiveSocialRatingService.updateSocialRating(id, ratingValue);

        return processedUser
                .map(user -> new ResponseEntity<>(user, HttpStatus.OK))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/regions/{id}/average-social-rating")
    public Mono<ResponseEntity<Double>> getAverageSocialRating(@PathVariable String id) {
        return reactiveRegionRepository.findById(id)
                .map(region -> new ResponseEntity<>(region.getAverageSocialRating(), HttpStatus.OK))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
