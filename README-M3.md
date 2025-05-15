# Milestone 3 - Key Transformation for XML to JSONObject

## Objective

This milestone adds an API that allows users to customize the transformation of all keys (tag and attribute names) during XML-to-JSONObject parsing. The transformation logic is provided by the user as a Java Functional Interface (`Function<String, String>`).

---

## API Description

```java
/**
 * Converts XML from a Reader into a JSONObject, transforming all tag and attribute keys
 * using the provided transformer function.
 *
 * @param reader         The XML source.
 * @param keyTransformer A {@link java.util.function.Function} that takes a key (String)
 *                       and returns the transformed key (String). The transformer can
 *                       implement any logic, such as adding a prefix or suffix.
 * @return A JSONObject containing the structured data from the XML string, with all keys transformed.
 * @throws JSONException If the XML is malformed or the transformer produces invalid keys.
 */
public static org.json.JSONObject toJSONObject(Reader reader, Function<String, String> keyTransformer)
```

- `keyTransformer` must be a Java 8+ `Function<String, String>`.
- You can pass any lambda or method reference, for example:
  - Add prefix: `key -> "my_" + key`
  - Add suffix: `key -> key + "_my"`
- As long as the input and output are both Strings, any transformation is allowed.

---

## Usage Example

```java
import org.json.XML;
import org.json.JSONObject;
import java.io.StringReader;
import java.util.function.Function;

String xml = "<foo id=\"123\"><bar>hello</bar></foo>";

// Example 1: Add prefix
Function<String, String> addPrefix = key -> "swe262_" + key;
JSONObject obj1 = XML.toJSONObject(new StringReader(xml), addPrefix);
System.out.println(obj1.toString(2));

// Example 2: Add suffix
Function<String, String> addSuffix = key -> key + "_swe262";
JSONObject obj2 = XML.toJSONObject(new StringReader(xml), addSuffix);
System.out.println(obj2.toString(2));
```

---

## Notes and Limitations

- You can implement any key transformation logic, but make sure the returned key:
  - Is not null or empty
  - Does not contain illegal characters (such as whitespace or control characters)
  - Does not cause key collisions (e.g., multiple different keys mapping to the same key)
- If the transformed key is invalid, the API will throw a JSONException.
- You do not need to modify the library code; just provide a valid transformer.

---

## Performance Discussion

- **In-library transformation (this method):**
  - Parses XML and transforms keys on the fly. Overall time complexity is O(N), where N is the length of the XML (number of characters or tags).
  - Only one pass through the XML is needed. The complexity of the key transformation depends on your transformer (usually O(1) or O(M), where M is the key length).
  - Memory usage is O(N), as only one JSONObject structure is stored.

- **Client-side post-processing (Milestone 1 approach):**
  - First parses XML to JSONObject (O(N)), then recursively traverses the JSONObject to transform keys (O(K)), where K is the number of key/value nodes in the JSON object.
  - Total time complexity is O(N + K); in the worst case, K ≈ N, so two passes are needed.
  - Memory usage is also O(N), but with additional temporary objects.

**Conclusion:**
- In-library transformation requires only one pass and is more efficient (better Big O).
- Client-side post-processing requires two passes and is less efficient.

---

## Unit Tests

This project includes unit tests for the Milestone 3 keyTransformer API in `src/test/java/org/json/junit/XMLTest.java`, including:

- `testTransformKeysWithPrefixStyle()`:
  - Tests adding a prefix (e.g., `swe262_`) to all XML keys and verifies the resulting JSONObject structure matches expectations.
  - Example usage:
    ```java
    Function<String, String> prefixer = k -> "swe262_" + k;
    JSONObject actual = XML.toJSONObject(reader, prefixer);
    ```
- `testTransformKeysWithSuffixStyle()`:
  - Tests adding a suffix (e.g., `_swe262`) to all XML keys and verifies the resulting JSONObject structure matches expectations.
  - Example usage:
    ```java
    Function<String, String> suffixer = s -> s + "_swe262";
    JSONObject actual = XML.toJSONObject(reader, suffixer);
    ```

These tests automatically compare the transformed JSON structure to the expected result to ensure correct API behavior.

---

## References
- [Java Function Interface](https://docs.oracle.com/javase/8/docs/api/java/util/function/Function.html)
- [org.json.XML API](https://github.com/stleary/JSON-java)
