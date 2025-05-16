# Milestone 5 - Asynchronous XML Processing with Key Transformation

## Objective

This milestone enhances the XML API by adding asynchronous processing capabilities for XML to JSONObject conversion. It builds on Milestone 3's key transformation feature, allowing for non-blocking XML processing with customized error handling.

---

## API Description

```java
/**
 * Processes XML data asynchronously with key transformation
 * 
 * @param reader          XML input source
 * @param keyTransformer  Function that transforms tag/attribute keys
 * @param exceptionHandler Callback for error handling
 * @return A Future representing pending completion of the conversion
 */
public static Future<JSONObject> toJSONObject(Reader reader, 
                                             Function<String, String> keyTransformer, 
                                             Consumer<Exception> exceptionHandler)
```

- `reader`: The XML data source to be processed.
- `keyTransformer`: A Function<String, String> that transforms XML tag and attribute names.
- `exceptionHandler`: A Consumer<Exception> for customized error handling.
- Returns a `Future<JSONObject>` allowing non-blocking processing of XML data.

---

## Implementation Details

The implementation uses Java's concurrency utilities:
- A private helper class `FutureJsonObject` that encapsulates an `ExecutorService`
- Thread management for asynchronous execution
- Proper resource cleanup when processing completes
- Error propagation through the provided exception handler

```java
private static class FutureJsonObject {
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();

    public Future<JSONObject> toJSONObject(Reader reader, Function keyTransformer) throws Exception {
        return taskExecutor.submit(() -> XML.toJSONObject(reader, keyTransformer));
    }

    public void stopFuture() {
        taskExecutor.shutdown();
    }
}
```

---

## Unit Tests

This project includes unit tests for the Milestone 5 asynchronous API in `src/test/java/org/json/junit/XMLTest.java`:

- `transformJSONObjectKeyAsynTest()`:
  - Verifies that the asynchronous API correctly transforms XML and produces expected output.
  - Tests the complete workflow using a test XML document with prefixed keys.

- `transformJSONObjectKeyAsynReturnTypeTest()`:
  - Ensures the returned Future correctly resolves to a JSONObject instance.

- `transformJSONObjectKeyAsynExceptionHandlerTest()`:
  - Validates that exception handling works correctly when invalid inputs are provided.
  - Verifies the exceptionHandler is invoked and null is returned for the Future result.