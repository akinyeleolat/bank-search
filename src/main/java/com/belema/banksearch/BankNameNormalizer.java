package com.belema.banksearch;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalises bank names and queries the same way so that matching is
 * case-, accent- and punctuation-insensitive.
 *
 * <ul>
 *   <li>Accents are stripped: "Société" becomes "societe". Letters that do not
 *       decompose into a base letter plus accent are folded too: "Ørsted" becomes
 *       "orsted", "Straße" becomes "strasse".</li>
 *   <li>Everything is lower-cased using {@link Locale#ROOT}.</li>
 *   <li>"&amp;" becomes "and".</li>
 *   <li>Apostrophes are dropped so "Joseph's" matches "josephs".</li>
 *   <li>A full stop between two single letters is dropped so "U.B.A" matches "uba".
 *       Any other full stop separates words, so "Nig.Ltd" is "nig ltd" and
 *       "J.P.Morgan" is "jp morgan".</li>
 *   <li>Any other non-alphanumeric run (spaces, hyphens, brackets, slashes) is a word separator.</li>
 * </ul>
 */
public final class  BankNameNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    /** Latin letters that NFKD leaves alone, mapped to their usual ASCII spelling. */
    private static final Map<Character, String> FOLDED_LETTERS = Map.of(
            'ø', "o", 'ł', "l", 'æ', "ae", 'œ', "oe", 'ß', "ss",
            'đ', "d", 'ð', "d", 'þ', "th", 'ı', "i", 'ħ', "h");
    private static final Pattern ABBREVIATION_DOT = Pattern.compile("(?<![a-z0-9])([a-z])\\.(?=[a-z](?![a-z0-9]))");
    private static final Pattern APOSTROPHES = Pattern.compile("['‘’`]");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private BankNameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKD);
        s = COMBINING_MARKS.matcher(s).replaceAll("");
        s = foldLetters(s.toLowerCase(Locale.ROOT)).replace("&", " and ");
        s = ABBREVIATION_DOT.matcher(s).replaceAll("$1");
        s = APOSTROPHES.matcher(s).replaceAll("");
        return SEPARATORS.matcher(s).replaceAll(" ").trim();
    }

    public static List<String> tokens(String raw) {
        String normalized = normalize(raw);
        return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split(" "));
    }

    private static String foldLetters(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String folded = FOLDED_LETTERS.get(c);
            if (folded == null) {
                out.append(c);
            } else {
                out.append(folded);
            }
        }
        return out.toString();
    }
}
