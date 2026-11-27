package dev.bulwark.users;

import java.time.LocalDate;

public record User(long id, String username, String email, String country, LocalDate joined) {
}
