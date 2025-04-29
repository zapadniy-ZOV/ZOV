package itmo.rshd.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RatingUpdate {
    private String userId;      // User who is giving the rating
    private String targetUserId; // User who is being rated
    private double ratingChange; // Positive for like, negative for dislike
} 