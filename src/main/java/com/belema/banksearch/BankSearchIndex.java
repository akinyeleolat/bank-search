package com.belema.banksearch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Word-level prefix trie for bank-name type-ahead.
 *
 * <p>For each bank it indexes every word of the normalised name, the whole name
 * with spaces removed ("firstbankofnigeria"), and each alias. Every trie node
 * stores the codes of all banks that have a key starting with that node's
 * prefix. A lookup therefore walks only the length of the query and never
 * traverses a subtree.
 *
 * <p>Ranking:
 * <ol>
 *   <li>Banks whose full name or one of whose aliases starts with the query come
 *       first, so "first ba" puts First Bank of Nigeria ahead of First City Monument
 *       Bank, and "fbn" puts First Bank (alias FBN) ahead of a less popular FBNQuest.</li>
 *   <li>Then by popularity, highest first.</li>
 *   <li>Then alphabetically, and finally by code so the order is deterministic.</li>
 * </ol>
 *
 * <p>Writes are not thread-safe. In a server, build a new index when the bank
 * list changes and swap it in through an {@code AtomicReference}. Reads on an
 * index that is no longer being written are safe.
 */
public final class BankSearchIndex {

    public static final int DEFAULT_LIMIT = 10;

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        final Set<String> bankCodes = new HashSet<>();
    }

    /** {@code compactNames} holds the name and each alias with spaces removed, for ranking. */
    private record Indexed(Bank bank, String normalizedName, Set<String> compactNames, Set<String> keys) {
    }

    private static final Comparator<Indexed> BY_POPULARITY_THEN_NAME =
            Comparator.comparingLong((Indexed i) -> i.bank().popularity()).reversed()
                    .thenComparing(Indexed::normalizedName)
                    .thenComparing(i -> i.bank().code());

    private final Node root = new Node();
    private final Map<String, Indexed> byCode = new HashMap<>();

    public static BankSearchIndex build(Collection<Bank> banks) {
        BankSearchIndex index = new BankSearchIndex();
        banks.forEach(index::insert);
        return index;
    }

    /**
     * Inserts a bank. If a bank with the same code exists, it is replaced.
     *
     * @throws IllegalArgumentException if the name has no searchable characters,
     *         e.g. it is only punctuation. The existing entry, if any, is kept.
     */
    public void insert(Bank bank) {
        Objects.requireNonNull(bank, "bank");
        String normalized = BankNameNormalizer.normalize(bank.name());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name has no searchable characters: " + bank.name());
        }
        remove(bank.code());

        String compact = normalized.replace(" ", "");

        Set<String> compactNames = new LinkedHashSet<>();
        compactNames.add(compact);
        Set<String> keys = new LinkedHashSet<>(BankNameNormalizer.tokens(bank.name()));
        keys.add(compact);
        for (String alias : bank.aliases()) {
            List<String> aliasTokens = BankNameNormalizer.tokens(alias);
            keys.addAll(aliasTokens);
            compactNames.add(String.join("", aliasTokens));
        }
        compactNames.remove("");
        keys.addAll(compactNames);

        for (String key : keys) {
            addKey(key, bank.code());
        }
        byCode.put(bank.code(), new Indexed(bank, normalized, Set.copyOf(compactNames), Set.copyOf(keys)));
    }

    /** Removes the bank with this code. Returns true if it was present. */
    public boolean remove(String code) {
        Indexed existing = byCode.remove(code);
        if (existing == null) {
            return false;
        }
        for (String key : existing.keys()) {
            removeKey(key, code);
        }
        return true;
    }

    public int size() {
        return byCode.size();
    }

    public List<Bank> suggest(String query) {
        return suggest(query, DEFAULT_LIMIT);
    }

    /**
     * Returns up to {@code limit} banks in which every word of the query is a
     * prefix of some word of the bank's name or of one of its aliases.
     */
    public List<Bank> suggest(String query, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        List<String> queryTokens = BankNameNormalizer.tokens(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<Set<String>> perToken = new ArrayList<>(queryTokens.size());
        for (String token : queryTokens) {
            Node node = find(token);
            if (node == null) {
                return List.of();
            }
            perToken.add(node.bankCodes);
        }
        // Start the intersection from the smallest set.
        perToken.sort(Comparator.comparingInt(Set::size));
        Set<String> matches = new HashSet<>(perToken.get(0));
        for (int i = 1; i < perToken.size() && !matches.isEmpty(); i++) {
            matches.retainAll(perToken.get(i));
        }

        String compactQuery = String.join("", queryTokens);
        Comparator<Indexed> ranking = Comparator
                .comparingInt((Indexed i) -> startsWithQuery(i, compactQuery) ? 0 : 1)
                .thenComparing(BY_POPULARITY_THEN_NAME);

        return matches.stream()
                .map(byCode::get)
                .sorted(ranking)
                .limit(limit)
                .map(Indexed::bank)
                .toList();
    }

    private static boolean startsWithQuery(Indexed indexed, String compactQuery) {
        for (String name : indexed.compactNames()) {
            if (name.startsWith(compactQuery)) {
                return true;
            }
        }
        return false;
    }

    private Node find(String prefix) {
        Node node = root;
        for (int i = 0; i < prefix.length() && node != null; i++) {
            node = node.children.get(prefix.charAt(i));
        }
        return node;
    }

    private void addKey(String key, String code) {
        Node node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.computeIfAbsent(key.charAt(i), c -> new Node());
            node.bankCodes.add(code);
        }
    }

    private void removeKey(String key, String code) {
        Deque<Node> path = new ArrayDeque<>();
        Node node = root;
        path.push(root);
        for (int i = 0; i < key.length(); i++) {
            node = node.children.get(key.charAt(i));
            if (node == null) {
                return; // already pruned while removing another key of the same bank
            }
            node.bankCodes.remove(code);
            path.push(node);
        }
        // Remove nodes no bank uses anymore. If a node's set is empty, its whole subtree is unused.
        for (int i = key.length() - 1; i >= 0; i--) {
            Node child = path.pop();
            if (!child.bankCodes.isEmpty()) {
                break;
            }
            path.peek().children.remove(key.charAt(i));
        }
    }
}
