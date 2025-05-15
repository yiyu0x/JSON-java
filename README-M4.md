# Milestone 4 - Streaming API for JSONObject

## Overview
This milestone introduces a streaming API for `JSONObject`, enabling users to perform functional-style, chainable operations on JSON data structures. The new API allows traversal and transformation of JSON nodes using Java Streams, making it easier to process hierarchical data in a flexible and memory-efficient way.

---

## Changes to Production Code (`JSONObject.java`)

### Added Streaming Method
- Implemented a new method:
  ```java
  public Stream<Object> toStream()
  ```
  This method returns a Java Stream of all nodes within the `JSONObject`, supporting traversal of the entire JSON hierarchy. The stream can be filtered, mapped, and collected using standard Java Stream operations.

- The stream includes all elements in the JSON structure, regardless of nesting depth, allowing for powerful and concise data processing pipelines.

### Example Usage
```java
JSONObject obj = XML.toJSONObject("<title>AAA</title>ASmith<title>BBB</title>BSmith");
// Print all nodes
obj.toStream().forEach(node -> System.out.println(node));
// Collect all title values (custom filter and mapping logic)
List<?> titles = obj.toStream()
    .filter(node -> ((Map<?, ?>) node).keySet().iterator().next().toString().contains("/title"))
    .map(node -> ((Map<?, ?>) node).values().iterator().next())
    .collect(Collectors.toList());
```

---

## Changes to Test Code (`JSONObjectStreamTest.java`)

### Unit Tests (New Style)

- The test class `JSONObjectStreamTest` now verifies the streaming API with a different coding style:
  - `extractBookPricesTest()`: Validates extracting all `price` values from a parsed XML book catalog using the stream API.
  - `countAllNodesTest()`: Asserts that the stream traverses the expected number of nodes in a complex JSON structure.
  - `verifyBookArraySizeTest()`: Ensures the stream can be used to determine the size of a nested `JSONArray` by parsing the last key index.

