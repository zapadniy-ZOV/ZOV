package itmo.rshd.model;

import java.util.List;

public class UserIdsRequest {
    private List<String> user_ids;

    public UserIdsRequest(List<String> user_ids) {
        this.user_ids = user_ids;
    }

    public List<String> getUser_ids() {
        return user_ids;
    }

    public void setUser_ids(List<String> user_ids) {
        this.user_ids = user_ids;
    }
} 