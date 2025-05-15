import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JSONObjectStreamTest {
    /**
     * Validate extracting prices from JSONArray using toStream().
     */
    @Test
    public void extractBookPricesTest() throws IOException {
        try (FileReader reader = new FileReader("src/test/java/org/json/junit/testXML/books.xml")) {
            JSONObject root = XML.toJSONObject(reader);
            JSONArray books = (JSONArray) root.query("/catalog/book");
            List<Object> expected = books.toList().stream()
                    .map(book -> ((Map<?, ?>) book).get("price"))
                    .collect(Collectors.toList());

            List<Object> actual = root.toStream()
                    .filter(node -> ((Map<?, ?>) node).keySet().iterator().next().toString().contains("/catalog/book"))
                    .filter(node -> ((Map<?, ?>) node).keySet().iterator().next().toString().contains("/price"))
                    .map(node -> ((Map<?, ?>) node).values().iterator().next())
                    .collect(Collectors.toList());

            assertEquals(expected, actual);
        }
    }

    /**
     * Check the total number of nodes in the JSONObject using toStream().
     */
    @Test
    public void countAllNodesTest() throws IOException {
        try (FileReader reader = new FileReader("src/test/java/org/json/junit/testXML/books.xml")) {
            JSONObject root = XML.toJSONObject(reader);
            long count = root.toStream().count();
            assertEquals(84, count);
        }
    }

    /**
     * Ensure the number of book objects matches the last index in the stream.
     */
    @Test
    public void verifyBookArraySizeTest() throws IOException {
        try (FileReader reader = new FileReader("src/test/java/org/json/junit/testXML/books.xml")) {
            JSONObject root = XML.toJSONObject(reader);
            JSONArray books = (JSONArray) root.query("/catalog/book");
            int expectedCount = books.length();

            List<Object> keyList = root.toStream()
                    .map(node -> ((Map<?, ?>) node).keySet().iterator().next())
                    .collect(Collectors.toList());
            String[] lastKeys = keyList.get(keyList.size() - 1).toString().substring(1).split("/");
            int actualCount = Integer.parseInt(lastKeys[2]);
            assertEquals(expectedCount, actualCount);
        }
    }
}