package com.belema.banksearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankSearchIndexTest {

    private BankSearchIndex index;

    @BeforeEach
    void setUp() {
        index = BankSearchIndex.build(List.of(
                new Bank("011", "First Bank of Nigeria", Set.of("FirstBank", "FBN"), 950),
                new Bank("214", "First City Monument Bank", Set.of("FCMB"), 700),
                new Bank("058", "Guaranty Trust Bank", Set.of("GTBank", "GTB", "GTCO"), 1000),
                new Bank("044", "Access Bank", 990),
                new Bank("221", "Stanbic IBTC Bank Plc.", 600),
                new Bank("559", "Coronation Merchant-Bank Ltd.", 50),
                new Bank("50211", "Kuda Microfinance Bank", Set.of("Kuda"), 800),
                new Bank("50515", "Moniepoint Microfinance Bank", Set.of("Moniepoint"), 850),
                new Bank("090267", "St. Joseph's Microfinance Bank", 20),
                new Bank("999001", "Société Générale Bank", 10),
                new Bank("033", "United Bank for Africa", Set.of("UBA"), 900)
        ));
    }

    private List<String> codes(String query) {
        return codes(query, BankSearchIndex.DEFAULT_LIMIT);
    }

    private List<String> codes(String query, int limit) {
        return index.suggest(query, limit).stream().map(Bank::code).toList();
    }

    @Test
    void matchesCaseInsensitively() {
        assertEquals(List.of("044"), codes("ACCESS"));
        assertEquals(List.of("044"), codes("aCc"));
    }

    @Test
    void ignoresPunctuationInNamesAndQueries() {
        assertEquals(List.of("221"), codes("stanbic-ibtc"));
        assertEquals(List.of("221"), codes("plc"));
        assertEquals(List.of("559"), codes("merchant bank"));
        assertEquals(List.of("090267"), codes("st josephs"));
        assertEquals(List.of("090267"), codes("joseph's"));
    }

    @Test
    void ignoresAccents() {
        assertEquals(List.of("999001"), codes("societe generale"));
        assertEquals(List.of("999001"), codes("Société"));
    }

    @Test
    void matchesWordsOtherThanTheFirst_orderedByPopularity() {
        assertEquals(List.of("50515", "50211", "090267"), codes("micro"));
    }

    @Test
    void commonWordReturnsTopTenByPopularity() {
        List<String> result = codes("bank");
        assertEquals(10, result.size());
        assertEquals("058", result.get(0));
        assertEquals("044", result.get(1));
        assertFalse(result.contains("999001"), "least popular bank falls outside the top 10");
    }

    @Test
    void fullNamePrefixOutranksPopularity() {
        BankSearchIndex local = BankSearchIndex.build(List.of(
                new Bank("011", "First Bank of Nigeria", 10),
                new Bank("214", "First City Monument Bank", 999)));
        List<String> result = local.suggest("first bank").stream().map(Bank::code).toList();
        assertEquals(List.of("011", "214"), result);
    }

    @Test
    void aliasPrefixOutranksPopularity() {
        BankSearchIndex local = BankSearchIndex.build(List.of(
                new Bank("011", "First Bank of Nigeria", Set.of("FBN"), 10),
                new Bank("060", "FBNQuest Merchant Bank", 999),
                new Bank("070", "Some Bank", Set.of("Other FBN"), 5000)));
        List<String> result = local.suggest("fbn").stream().map(Bank::code).toList();
        assertEquals(List.of("060", "011", "070"), result);
    }

    @Test
    void ties_areBrokenAlphabetically() {
        BankSearchIndex local = BankSearchIndex.build(List.of(
                new Bank("2", "Zenith Microfinance Bank", 100),
                new Bank("1", "Alpha Microfinance Bank", 100)));
        List<String> result = local.suggest("micro").stream().map(Bank::code).toList();
        assertEquals(List.of("1", "2"), result);
    }

    @Test
    void matchesNameTypedWithoutSpaces() {
        assertEquals("011", codes("firstbank").get(0));
        assertEquals(List.of("044"), codes("accessbank"));
    }

    @Test
    void matchesAliases() {
        assertEquals(List.of("058"), codes("gtb"));
        assertEquals(List.of("058"), codes("GTCO"));
        assertEquals(List.of("033"), codes("uba"));
        assertEquals(List.of("214"), codes("fcmb"));
    }

    @Test
    void everyQueryWordMustMatch() {
        assertEquals(List.of("50211"), codes("kuda micro"));
        assertEquals(List.of(), codes("kuda access"));
    }

    @Test
    void respectsLimitAndReturnsNoDuplicates() {
        assertEquals(3, codes("bank", 3).size());
        List<String> all = codes("bank", 50);
        assertEquals(all.size(), Set.copyOf(all).size());
        assertEquals(11, all.size());
    }

    @Test
    void blankOrUnmatchedQueriesReturnEmpty() {
        assertEquals(List.of(), codes("   "));
        assertEquals(List.of(), codes("!!!"));
        assertEquals(List.of(), codes(null));
        assertEquals(List.of(), codes("zzz"));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> index.suggest("bank", 0));
    }

    @Test
    void insertWithExistingCodeReplacesTheOldEntry() {
        index.insert(new Bank("044", "Diamond Bank", 990));
        assertEquals(List.of(), codes("access"));
        assertEquals(List.of("044"), codes("diamond"));
        assertEquals(11, index.size());
    }

    @Test
    void removeDropsBankFromSuggestions() {
        assertTrue(index.remove("044"));
        assertEquals(List.of(), codes("access"));
        assertFalse(index.remove("044"));
        assertEquals(10, index.size());
    }

    @Test
    void removedPrefixesStillServeOtherBanks() {
        index.remove("011");
        assertEquals(List.of("214"), codes("first"));
    }

    @Test
    void matchesNamesWithNonDecomposingLetters() {
        index.insert(new Bank("777", "Ørsted Bank", 5));
        assertEquals(List.of("777"), codes("orsted"));
        assertEquals(List.of("777"), codes("Ørsted"));
    }

    @Test
    void matchesWordAfterFullStopWithoutSpace() {
        index.insert(new Bank("050", "Ecobank Nig.Ltd", 5));
        assertEquals(List.of("050"), codes("ecobank ltd"));
        assertEquals(List.of("050"), codes("ecobank nig"));
    }

    @Test
    void rejectsNameWithNoSearchableCharacters_andKeepsExistingEntry() {
        assertThrows(IllegalArgumentException.class, () -> index.insert(new Bank("1", "\u2014", 5)));
        assertEquals(11, index.size());

        assertThrows(IllegalArgumentException.class, () -> index.insert(new Bank("044", "!!!", 990)));
        assertEquals(List.of("044"), codes("access"));
        assertEquals(11, index.size());
    }

    @Test
    void findsWordAfterInitialsWithoutSpace() {
        index.insert(new Bank("901", "J.P.Morgan Bank", 5));
        assertEquals(List.of("901"), codes("morgan"));
        assertEquals(List.of("901"), codes("jp"));
    }

    @Test
    void handlesAFullSizedList() {
        List<Bank> banks = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            banks.add(new Bank("B" + i, "Bank Number " + i + " Microfinance", i));
        }
        BankSearchIndex big = BankSearchIndex.build(banks);
        assertEquals(900, big.size());
        List<Bank> top = big.suggest("micro");
        assertEquals(10, top.size());
        assertEquals("B899", top.get(0).code());
        assertEquals(List.of("B42"), big.suggest("bank number 42 micro").stream().map(Bank::code).limit(1).toList());
    }
}
