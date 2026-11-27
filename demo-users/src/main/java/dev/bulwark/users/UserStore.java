package dev.bulwark.users;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Read-only fake users generated from a fixed seed, so every container serves identical data.
 */
@Component
public class UserStore {

    private static final String[] FIRST = {"ada", "linus", "grace", "alan", "margaret", "dennis", "barbara", "ken", "radia", "tim"};
    private static final String[] COUNTRIES = {"GB", "US", "NG", "DE", "IN", "BR", "JP", "CA"};
    private static final int USER_COUNT = 500;

    private final List<User> users;

    public UserStore() {
        Random random = new Random(7);
        LocalDate today = LocalDate.now();
        List<User> generated = new ArrayList<>(USER_COUNT);
        for (long id = 1; id <= USER_COUNT; id++) {
            String username = FIRST[random.nextInt(FIRST.length)] + id;
            generated.add(new User(
                    id,
                    username,
                    username + "@example.com",
                    COUNTRIES[random.nextInt(COUNTRIES.length)],
                    today.minusDays(random.nextInt(3 * 365))));
        }
        this.users = Collections.unmodifiableList(generated);
    }

    public List<User> list(int offset, int limit) {
        return users.stream().skip(offset).limit(limit).toList();
    }

    public Optional<User> find(long id) {
        if (id < 1 || id > users.size()) {
            return Optional.empty();
        }
        return Optional.of(users.get((int) id - 1));
    }
}
