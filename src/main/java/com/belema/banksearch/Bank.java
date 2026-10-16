package com.belema.banksearch;

import java.util.Objects;
import java.util.Set;

/**
 * A destination bank as shown in the beneficiary picker.
 *
 * @param code       NIP / CBN institution code (unique key)
 * @param name       display name, e.g. "Guaranty Trust Bank"
 * @param aliases    names customers actually type, e.g. "GTBank", "GTB"
 * @param popularity ranking weight, e.g. transfer count over the last 30 days
 */
public record Bank(String code, String name, Set<String> aliases, long popularity) {

    public Bank {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (popularity < 0) throw new IllegalArgumentException("popularity must be >= 0");
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    public Bank(String code, String name, long popularity) {
        this(code, name, Set.of(), popularity);
    }
}
