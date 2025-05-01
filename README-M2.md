# Milestone2

## Overview
In Milestone 2, we enhanced the `XML` class to support key transformation during XML to JSON conversion as well as JSONPointer functionality. These features provide users with more flexibility when parsing XML documents and manipulating the resulting JSON.

## Changes to Production Code (XML.java)

### Added New Methods
Added two new overloaded methods to the XML 

```java
static JSONObject toJSONObject(Reader reader, JSONPointer path)
```
This method parses XML into a JSONObject and returns only the portion of the document specified by the JSONPointer path.

```java
static JSONObject toJSONObject(Reader reader, JSONPointer path, JSONObject replacement)
```
This method parses XML into a JSONObject, replaces the portion specified by the JSONPointer path with the provided replacement JSONObject, and returns the complete modified object.

### Implementation Details
- The key transformation is applied during the XML parsing process, not afterwards as a separate pass.
- All keys (tag names and attribute names) are transformed using the provided function.
- The JSONPointer functionality allows for precise targeting of specific elements within the XML structure.
- The implementation maintains compatibility with existing XML parsing features.

### Performance Implications

The JSONPointer implementation also provides efficiency benefits:
1. **Targeted Processing**: Only retrieves or modifies the specific parts of the XML document that are needed.
2. **Reduced Memory Footprint**: When extracting subsets of large XML documents, avoids keeping the entire document in memory.
3. **Optimized Path Traversal**: Efficiently navigates through the XML structure based on the provided path.
4. **Direct Replacement**: When using the replacement functionality, modifies the target location without rebuilding the entire object structure.

## Changes to Test Code (XMLTest.java)

### Added New Test Methods
Added unit tests to verify the new functionality (lines 1432-1540 in XMLTest.java):

1. `testToJSONObject_extractStreetTest()` (line 1432): Tests extracting a street address using JSONPointer with a trailing slash in the path.
2. `testToJSONObject_extractZipcodeTest()` (line 1453): Tests extracting a zipcode using JSONPointer, verifying that numeric content is preserved as string when extracted.
3. `testToJSONObject_extractNicknameTest()` (line 1475): Tests extracting a nickname using JSONPointer, verifying that whitespace is preserved when extracted.
4. `testToJSONObjectFunc_withValidInput_shouldReplace()` (line 1496): Tests replacing XML content at a specific path with a JSONPointer and replacement object.
5. `testToJSONObjectFunc_withNonexistentPath_shouldNotReplace()` (line 1515): Tests that replacement does not occur when the JSONPointer path does not exist in the document.

### Test Coverage
The tests verify that:
- Keys are correctly transformed during parsing
- The transformation applies to both element names and attribute names
- JSONPointer paths correctly identify and extract the targeted portions of XML documents
- Replacement functionality correctly modifies the specified portions of the document
- The structure of the resulting JSONObject is as expected
- Proper error handling occurs for invalid inputs
- Path traversal works correctly with both existing and non-existent paths
- Whitespace and numeric content handling is consistent

