# Bank name type-ahead (Belema assessment, Q2)

An in-memory, word-level prefix trie that suggests destination banks as the customer types.

## Requirements
- Java 17 or later
- Maven 3.8 or later

## Run the tests
```bash
mvn test
```

## Usage
```java
BankSearchIndex index = BankSearchIndex.build(List.of(
    new Bank("058", "Guaranty Trust Bank", Set.of("GTBank", "GTB"), 1000),
    new Bank("044", "Access Bank", 990)));

index.suggest("gtb");          // [Guaranty Trust Bank]
index.suggest("bank", 10);     // up to 10 results, best ranked first
index.insert(new Bank("033", "United Bank for Africa", Set.of("UBA"), 900));
index.remove("044");
```

## Layout
- `Bank`: the bank record (code, name, aliases, popularity)
- `BankNameNormalizer`: makes matching case-, accent- and punctuation-insensitive
- `BankSearchIndex`: the trie, with insert, remove and suggest
- `src/test/...`: JUnit 5 tests

See `NOTE.md` for the design choice, complexity and deployment notes.
