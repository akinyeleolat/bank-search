package com.belema.banksearch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankNameNormalizerTest {

    @Test
    void lowercasesAndCollapsesSeparators() {
        assertEquals("coronation merchant bank ltd",
                BankNameNormalizer.normalize("  Coronation   Merchant-Bank Ltd. "));
    }

    @Test
    void dropsApostrophesAndFullStopsAndExpandsAmpersand() {
        assertEquals("st josephs micro finance and co",
                BankNameNormalizer.normalize("St. Joseph's Micro/Finance & Co."));
        assertEquals("uba", BankNameNormalizer.normalize("U.B.A"));
        assertEquals("josephs", BankNameNormalizer.normalize("Joseph‘s"));
        assertEquals("josephs", BankNameNormalizer.normalize("Joseph’s"));
    }

    @Test
    void stripsAccents() {
        assertEquals("societe generale", BankNameNormalizer.normalize("Société Générale"));
    }

    @Test
    void foldsLettersThatDoNotDecompose() {
        assertEquals(List.of("orsted", "lodz", "strasse"),
                BankNameNormalizer.tokens("Ørsted Łódź Straße"));
        assertEquals("aegir oeuvre thor", BankNameNormalizer.normalize("Ægir Œuvre Þor"));
    }

    @Test
    void fullStopBetweenSingleLettersIsDropped_otherFullStopsSeparateWords() {
        assertEquals(List.of("ecobank", "nig", "ltd"), BankNameNormalizer.tokens("Ecobank Nig.Ltd"));
        assertEquals(List.of("uba"), BankNameNormalizer.tokens("U.B.A."));
        assertEquals(List.of("fcmb", "bank"), BankNameNormalizer.tokens("F.C.M.B Bank"));
        assertEquals(List.of("st", "joseph"), BankNameNormalizer.tokens("St.Joseph"));
        assertEquals(List.of("jp", "morgan"), BankNameNormalizer.tokens("J.P.Morgan"));
        assertEquals(List.of("bank", "of", "ny", "mellon"), BankNameNormalizer.tokens("Bank of N.Y.Mellon"));
        assertEquals(List.of("a", "bank", "ltd"), BankNameNormalizer.tokens("A.Bank Ltd"));
        assertEquals(List.of("1", "2"), BankNameNormalizer.tokens("1.2"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", BankNameNormalizer.normalize(null));
        assertEquals(List.of(), BankNameNormalizer.tokens("  -- "));
    }

    @Test
    void splitsIntoTokens() {
        assertEquals(List.of("stanbic", "ibtc", "bank", "plc"),
                BankNameNormalizer.tokens("Stanbic IBTC Bank Plc."));
    }
}
