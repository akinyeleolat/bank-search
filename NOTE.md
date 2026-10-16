# Q2 — Bank-name lookahead: design note

**Assumptions.**
- The list has about 900 banks, each keyed by a unique NIP institution code. It changes rarely (weeks, not minutes), and the server owns it.
- Popularity means 30-day transfer counts across all customers. Each customer's own recent banks are a UI concern and out of scope here.
- Matching is word-prefix only. Typo tolerance is not required, and could be added later with edit-distance search over the same trie.
- Ops curates the aliases (GTB, FCMB, UBA).
- Names are in Latin script. The client is a mobile app, so this Java code ports directly to Kotlin.

**Choice.** I used a word-level prefix trie. Every word of a bank's normalised name is a key, as is the whole name with spaces removed ("firstbankofnigeria") and each alias ("GTB", "FCMB"). Each trie node stores the set of bank codes that have a key starting with that node's prefix. A lookup walks the query's characters and reads the result straight off the node, with no subtree traversal.

**Alternatives considered.**
- *Linear scan using `contains()`.* With 900 banks this is fast enough. It does substring matching, though, so "ank" matches every "Bank", and the work per keystroke grows with N.
- *Sorted array of (word, bankCode) pairs with binary search.* This is equally valid and uses less memory. Lookup is O(log T + m), but it needs a range scan and de-duplication. The trie keeps lookup at O(L) and makes upsert and removal simple. Its extra memory, a few tens of thousands of references, is trivial.
- *Elasticsearch.* Ruled out by the brief, and not justified for this data size.

**Complexity.** N is the number of banks and T the total characters across all keys (about 60 per bank). L is the query length, q the number of query words, m the number of matches (m ≤ N) and k the result limit.

| Operation | Cost |
|---|---|
| Insert one bank | O(t), where t is that bank's key characters |
| Build the index | O(T) |
| Remove or replace a bank | O(t) |
| Lookup | O(L + q·m + m log m) to walk, intersect and rank |
| Space | O(T) nodes plus O(T) code references, well under 1 MB |

Caching the top k at each node would give O(L + k) lookups. That isn't worth the rebuild it forces whenever popularity changes, given this data size.

**Normalisation.** Names and queries go through the same normaliser. It applies NFKD and strips accents ("Société" becomes "societe"), and folds Latin letters that have no accent to strip ("Ørsted" becomes "orsted", "Straße" becomes "strasse"). It lower-cases with the root locale, turns "&" into "and", and drops apostrophes ("Joseph's" becomes "josephs"). A full stop between two single letters is dropped ("U.B.A" becomes "uba"); any other full stop separates words, so "Nig.Ltd" is still found by "ltd" and "J.P.Morgan" by "morgan". Every other non-alphanumeric run, such as a space, hyphen or slash, separates words. A bank whose name normalises to nothing (only punctuation, say) is rejected on insert rather than stored where no search can reach it.

**Matching on words other than the first.** Every word of the name is indexed, so "micro" finds every microfinance bank and "bank" finds most of the list. In a multi-word query each word must prefix-match some word of the bank's name ("kuda micro"). Customers who type a name without spaces ("firstbank") are matched through the space-free key.

**Ordering and limits.** Results are ranked in this order:
1. Banks whose full name or one of whose aliases starts with the query, so "first ba" puts First Bank ahead of FCMB, and "fbn" puts First Bank (alias FBN) ahead of a less popular FBNQuest.
2. Popularity, using 30-day transfer counts recomputed daily.
3. Alphabetical name, then code, so the order is deterministic.

The default limit is 10. On the client, the customer's own recent beneficiaries' banks appear above these suggestions.

**Where it lives and how it is refreshed.** It runs in both places, with the server as the authority.
- *Server.* The server holds the canonical list, built from the NIP institution list plus admin edits, including an active flag, aliases and popularity. It serves `GET /v1/banks` with a version and an ETag. The same library backs web and USSD search. When the list changes, the server builds a new index and swaps it in through an `AtomicReference`, so reads never lock.
- *Client.* The app ships with a bundled snapshot so the first launch works offline. On launch or foreground it makes a conditional GET with `If-None-Match`; normally that returns 304, and on 200 the app rebuilds its local index, which takes milliseconds. Every keystroke is served locally with no network round trip.
- *Validation.* A stale client cache can never send money to a deactivated bank, because the transfer API validates the bank code on the server.

The logic is about 150 lines and ports directly to Kotlin or Swift if the app is native.
