package org.json;

/*
Public Domain.
*/

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.function.Function;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * This provides static methods to convert an XML text into a JSONObject, and to
 * covert a JSONObject into an XML text.
 *
 * @author JSON.org
 * @version 2016-08-10
 */
@SuppressWarnings("boxing")
public class XML {

    /**
     * Constructs a new XML object.
     */
    public XML() {
    }

    /** The Character '&amp;'. */
    public static final Character AMP = '&';

    /** The Character '''. */
    public static final Character APOS = '\'';

    /** The Character '!'. */
    public static final Character BANG = '!';

    /** The Character '='. */
    public static final Character EQ = '=';

    /** The Character <pre>{@code '>'. }</pre>*/
    public static final Character GT = '>';

    /** The Character '&lt;'. */
    public static final Character LT = '<';

    /** The Character '?'. */
    public static final Character QUEST = '?';

    /** The Character '"'. */
    public static final Character QUOT = '"';

    /** The Character '/'. */
    public static final Character SLASH = '/';

    /**
     * Null attribute name
     */
    public static final String NULL_ATTR = "xsi:nil";

    /**
     * Represents the XML attribute name for specifying type information.
     */
    public static final String TYPE_ATTR = "xsi:type";

    /**
     * Creates an iterator for navigating Code Points in a string instead of
     * characters. Once Java7 support is dropped, this can be replaced with
     * <code>
     * string.codePoints()
     * </code>
     * which is available in Java8 and above.
     *
     * @see <a href=
     *      "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
     */
    private static Iterable<Integer> codePointIterator(final String string) {
        return new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    private int nextIndex = 0;
                    private int length = string.length();

                    @Override
                    public boolean hasNext() {
                        return this.nextIndex < this.length;
                    }

                    @Override
                    public Integer next() {
                        int result = string.codePointAt(this.nextIndex);
                        this.nextIndex += Character.charCount(result);
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Replace special characters with XML escapes:
     *
     * <pre>{@code
     * &amp; (ampersand) is replaced by &amp;amp;
     * &lt; (less than) is replaced by &amp;lt;
     * &gt; (greater than) is replaced by &amp;gt;
     * &quot; (double quote) is replaced by &amp;quot;
     * &apos; (single quote / apostrophe) is replaced by &amp;apos;
     * }</pre>
     *
     * @param string
     *            The string to be escaped.
     * @return The escaped string.
     */
    public static String escape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (final int cp : codePointIterator(string)) {
            switch (cp) {
            case '&':
                sb.append("&amp;");
                break;
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\'':
                sb.append("&apos;");
                break;
            default:
                if (mustEscape(cp)) {
                    sb.append("&#x");
                    sb.append(Integer.toHexString(cp));
                    sb.append(';');
                } else {
                    sb.appendCodePoint(cp);
                }
            }
        }
        return sb.toString();
    }

    /**
     * @param cp code point to test
     * @return true if the code point is not valid for an XML
     */
    private static boolean mustEscape(int cp) {
        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
         *
         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
         *
         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF.
         */
        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
        // all ISO control characters are out of range except tabs and new lines
        return (Character.isISOControl(cp)
                && cp != 0x9
                && cp != 0xA
                && cp != 0xD
            ) || !(
                // valid the range of acceptable characters that aren't control
                (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF)
            )
        ;
    }

    /**
     * Removes XML escapes from the string.
     *
     * @param string
     *            string to remove escapes from
     * @return string with converted entities
     */
    public static String unescape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, length = string.length(); i < length; i++) {
            char c = string.charAt(i);
            if (c == '&') {
                final int semic = string.indexOf(';', i);
                if (semic > i) {
                    final String entity = string.substring(i + 1, semic);
                    sb.append(XMLTokener.unescapeEntity(entity));
                    // skip past the entity we just parsed.
                    i += entity.length() + 1;
                } else {
                    // this shouldn't happen in most cases since the parser
                    // errors on unclosed entries.
                    sb.append(c);
                }
            } else {
                // not part of an entity
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Throw an exception if the string contains whitespace. Whitespace is not
     * allowed in tagNames and attributes.
     *
     * @param string
     *            A string.
     * @throws JSONException Thrown if the string contains whitespace or is empty.
     */
    public static void noSpace(String string) throws JSONException {
        int i, length = string.length();
        if (length == 0) {
            throw new JSONException("Empty string.");
        }
        for (i = 0; i < length; i += 1) {
            if (Character.isWhitespace(string.charAt(i))) {
                throw new JSONException("'" + string
                        + "' contains a space character.");
            }
        }
    }

    /**
     * Scan the content following the named tag, attaching it to the context.
     *
     * @param x
     *            The XMLTokener containing the source string.
     * @param context
     *            The JSONObject that will include the new material.
     * @param name
     *            The tag name.
     * @param config
     *            The XML parser configuration.
     * @param currentNestingDepth
     *            The current nesting depth.
     * @return true if the close tag is processed.
     * @throws JSONException Thrown if any parsing error occurs.
     */
    private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, int currentNestingDepth)
            throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();

        // <!

        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            } else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } else if (token == QUEST) {

            // <?
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {

            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        } else {
            tagName = (String) token;
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;
            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                // attribute = value
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.isConvertNilAttributeToNull()
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        } else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        } else if (!nilAttributeFound) {
                            Object obj = stringToValue((String) token);
                            if (obj instanceof Boolean) {
                                jsonObject.accumulate(string,
                                        config.isKeepBooleanAsString()
                                                ? ((String) token)
                                                : obj);
                            } else if (obj instanceof Number) {
                                jsonObject.accumulate(string,
                                        config.isKeepNumberAsString()
                                                ? ((String) token)
                                                : obj);
                            } else {
                                jsonObject.accumulate(string, stringToValue((String) token));
                            }
                        }
                        token = null;
                    } else {
                        jsonObject.accumulate(string, "");
                    }


                } else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");
                        }
                    }
                    return false;

                } else if (token == GT) {
                    // Content, between <...> and </...>
                    for (;;) {
                        token = x.nextContent();
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        } else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));
                                } else {
                                    Object obj = stringToValue((String) token);
                                    if (obj instanceof Boolean) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepBooleanAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else if (obj instanceof Number) {
                                        jsonObject.accumulate(config.getcDataTagName(),
                                                config.isKeepNumberAsString()
                                                        ? ((String) token)
                                                        : obj);
                                    } else {
                                        jsonObject.accumulate(config.getcDataTagName(), stringToValue((String) token));
                                    }
                                }
                            }

                        } else if (token == LT) {
                            // Nested element
                            if (currentNestingDepth == config.getMaxNestingDepth()) {
                                throw x.syntaxError("Maximum nesting depth of " + config.getMaxNestingDepth() + " reached");
                            }

                            if (parse(x, jsonObject, tagName, config, currentNestingDepth + 1)) {
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1
                                            && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        if (!config.shouldTrimWhiteSpace()) {
                                            removeEmpty(jsonObject, config);
                                        }
                                        context.accumulate(tagName, jsonObject);
                                    }
                                }

                                return false;
                            }
                        }
                    }
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    /**
     * This method removes any JSON entry which has the key set by XMLParserConfiguration.cDataTagName
     * and contains whitespace as this is caused by whitespace between tags. See test XMLTest.testNestedWithWhitespaceTrimmingDisabled.
     * @param jsonObject JSONObject which may require deletion
     * @param config The XMLParserConfiguration which includes the cDataTagName
     */
    private static void removeEmpty(final JSONObject jsonObject, final XMLParserConfiguration config) {
        if (jsonObject.has(config.getcDataTagName()))  {
            final Object s = jsonObject.get(config.getcDataTagName());
            if (s instanceof String) {
                if (isStringAllWhiteSpace(s.toString())) {
                    jsonObject.remove(config.getcDataTagName());
                }
            }
            else if (s instanceof JSONArray) {
                final JSONArray sArray = (JSONArray) s;
                for (int k = sArray.length()-1; k >= 0; k--){
                    final Object eachString = sArray.get(k);
                    if (eachString instanceof String) {
                        String s1 = (String) eachString;
                        if (isStringAllWhiteSpace(s1)) {
                            sArray.remove(k);
                        }
                    }
                }
                if (sArray.isEmpty()) {
                    jsonObject.remove(config.getcDataTagName());
                }
            }
        }
    }

    private static boolean isStringAllWhiteSpace(final String s) {
        for (int k = 0; k<s.length(); k++){
            final char eachChar = s.charAt(k);
            if (!Character.isWhitespace(eachChar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * direct copy of {@link JSONObject#stringToNumber(String)} to maintain Android support.
     */
    private static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                // Use a BigDecimal all the time so we keep the original
                // representation. BigDecimal doesn't support -0.0, ensure we
                // keep that by forcing a decimal.
                try {
                    BigDecimal bd = new BigDecimal(val);
                    if(initial == '-' && BigDecimal.ZERO.compareTo(bd)==0) {
                        return Double.valueOf(-0.0);
                    }
                    return bd;
                } catch (NumberFormatException retryAsDouble) {
                    // this is to support "Hex Floats" like this: 0x1.0P-1074
                    try {
                        Double d = Double.valueOf(val);
                        if(d.isNaN() || d.isInfinite()) {
                            throw new NumberFormatException("val ["+val+"] is not a valid number.");
                        }
                        return d;
                    } catch (NumberFormatException ignore) {
                        throw new NumberFormatException("val ["+val+"] is not a valid number.");
                    }
                }
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            if(initial == '0' && val.length() > 1) {
                char at1 = val.charAt(1);
                if(at1 >= '0' && at1 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            } else if (initial == '-' && val.length() > 2) {
                char at1 = val.charAt(1);
                char at2 = val.charAt(2);
                if(at1 == '0' && at2 >= '0' && at2 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)

            // BigInteger down conversion: We use a similar bitLength compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived.
            BigInteger bi = new BigInteger(val);
            if(bi.bitLength() <= 31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength() <= 63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }

    /**
     * direct copy of {@link JSONObject#isDecimalNotation(String)} to maintain Android support.
     */
    private static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }

    /**
     * This method tries to convert the given string value to the target object
     * @param string String to convert
     * @param typeConverter value converter to convert string to integer, boolean e.t.c
     * @return JSON value of this string or the string
     */
    public static Object stringToValue(String string, XMLXsiTypeConverter<?> typeConverter) {
        if(typeConverter != null) {
            return typeConverter.convert(string);
        }
        return stringToValue(string);
    }

    /**
     * This method is the same as {@link JSONObject#stringToValue(String)}.
     *
     * @param string String to convert
     * @return JSON value of this string or the string
     */
    // To maintain compatibility with the Android API, this method is a direct copy of
    // the one in JSONObject. Changes made here should be reflected there.
    // This method should not make calls out of the XML object.
    public static Object stringToValue(String string) {
        if ("".equals(string)) {
            return string;
        }

        // check JSON key words true/false/null
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                return stringToNumber(string);
            } catch (Exception ignore) {
            }
        }
        return string;
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param string
     *            The source string.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string) throws JSONException {
        return toJSONObject(string, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param reader The XML source reader.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader) throws JSONException {
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, boolean keepStrings) throws JSONException {
        if(keepStrings) {
            return toJSONObject(reader, XMLParserConfiguration.KEEP_STRINGS);
        }
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All numbers are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     * All booleans are converted as strings, for true, false will not be coerced to
     * booleans but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     *
     * @param reader The XML source reader.
     * @param keepNumberAsString If true, then numeric values will not be coerced into
     *  numeric values and will instead be left as strings
     * @param keepBooleanAsString If true, then boolean values will not be coerced into
     *      *  numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, boolean keepNumberAsString, boolean keepBooleanAsString) throws JSONException {
        XMLParserConfiguration xmlParserConfiguration = new XMLParserConfiguration();
        if(keepNumberAsString) {
            xmlParserConfiguration = xmlParserConfiguration.withKeepNumberAsString(keepNumberAsString);
        }
        if(keepBooleanAsString) {
            xmlParserConfiguration = xmlParserConfiguration.withKeepBooleanAsString(keepBooleanAsString);
        }
        return toJSONObject(reader, xmlParserConfiguration);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param config Configuration options for the parser
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, XMLParserConfiguration config) throws JSONException {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader, config);
        while (x.more()) {
            x.skipPast("<");
            if(x.more()) {
                parse(x, jo, null, config, 0);
            }
        }
        return jo;
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
        return toJSONObject(new StringReader(string), keepStrings);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All numbers are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     * All booleans are converted as strings, for true, false will not be coerced to
     * booleans but will instead be the exact value as seen in the XML document depending
     * on how flag is set.
     *
     * @param string
     *            The source string.
     * @param keepNumberAsString If true, then numeric values will not be coerced into
     *  numeric values and will instead be left as strings
     * @param keepBooleanAsString If true, then boolean values will not be coerced into
     *  numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, boolean keepNumberAsString, boolean keepBooleanAsString) throws JSONException {
        return toJSONObject(new StringReader(string), keepNumberAsString, keepBooleanAsString);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param config Configuration options for the parser
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, XMLParserConfiguration config) throws JSONException {
        return toJSONObject(new StringReader(string), config);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(Object object) throws JSONException {
        return toString(object, null, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName) {
        return toString(object, tagName, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, final XMLParserConfiguration config)
            throws JSONException {
        return toString(object, tagName, config, 0, 0);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string,
     * either pretty print or single-lined depending on indent factor.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @param indent
     *            The current ident level in spaces.
     * @return
     * @throws JSONException
     */
    private static String toString(final Object object, final String tagName, final XMLParserConfiguration config, int indentFactor, int indent)
            throws JSONException {
        StringBuilder sb = new StringBuilder();
        JSONArray ja;
        JSONObject jo;
        String string;

        if (object instanceof JSONObject) {

            // Emit <tagName>
            if (tagName != null) {
                sb.append(indent(indent));
                sb.append('<');
                sb.append(tagName);
                sb.append('>');
                if(indentFactor > 0){
                    sb.append("\n");
                    indent += indentFactor;
                }
            }

            // Loop thru the keys.
            // don't use the new entrySet accessor to maintain Android Support
            jo = (JSONObject) object;
            for (final String key : jo.keySet()) {
                Object value = jo.opt(key);
                if (value == null) {
                    value = "";
                } else if (value.getClass().isArray()) {
                    value = new JSONArray(value);
                }

                // Emit content in body
                if (key.equals(config.getcDataTagName())) {
                    if (value instanceof JSONArray) {
                        ja = (JSONArray) value;
                        int jaLength = ja.length();
                        // don't use the new iterator API to maintain support for Android
                        for (int i = 0; i < jaLength; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            Object val = ja.opt(i);
                            sb.append(escape(val.toString()));
                        }
                    } else {
                        sb.append(escape(value.toString()));
                    }

                    // Emit an array of similar keys

                } else if (value instanceof JSONArray) {
                    ja = (JSONArray) value;
                    int jaLength = ja.length();
                    // don't use the new iterator API to maintain support for Android
                    for (int i = 0; i < jaLength; i++) {
                        Object val = ja.opt(i);
                        if (val instanceof JSONArray) {
                            sb.append('<');
                            sb.append(key);
                            sb.append('>');
                            sb.append(toString(val, null, config, indentFactor, indent));
                            sb.append("</");
                            sb.append(key);
                            sb.append('>');
                        } else {
                            sb.append(toString(val, key, config, indentFactor, indent));
                        }
                    }
                } else if ("".equals(value)) {
                    if (config.isCloseEmptyTag()){
                        sb.append(indent(indent));
                        sb.append('<');
                        sb.append(key);
                        sb.append(">");
                        sb.append("</");
                        sb.append(key);
                        sb.append(">");
                        if (indentFactor > 0) {
                            sb.append("\n");
                        }
                    }else {
                        sb.append(indent(indent));
                        sb.append('<');
                        sb.append(key);
                        sb.append("/>");
                        if (indentFactor > 0) {
                            sb.append("\n");
                        }
                    }

                    // Emit a new tag <k>

                } else {
                    sb.append(toString(value, key, config, indentFactor, indent));
                }
            }
            if (tagName != null) {

                // Emit the </tagName> close tag
                sb.append(indent(indent - indentFactor));
                sb.append("</");
                sb.append(tagName);
                sb.append('>');
                if(indentFactor > 0){
                    sb.append("\n");
                }
            }
            return sb.toString();

        }

        if (object != null && (object instanceof JSONArray ||  object.getClass().isArray())) {
            if(object.getClass().isArray()) {
                ja = new JSONArray(object);
            } else {
                ja = (JSONArray) object;
            }
            int jaLength = ja.length();
            // don't use the new iterator API to maintain support for Android
            for (int i = 0; i < jaLength; i++) {
                Object val = ja.opt(i);
                // XML does not have good support for arrays. If an array
                // appears in a place where XML is lacking, synthesize an
                // <array> element.
                sb.append(toString(val, tagName == null ? "array" : tagName, config, indentFactor, indent));
            }
            return sb.toString();
        }


        string = (object == null) ? "null" : escape(object.toString());
        String indentationSuffix = (indentFactor > 0) ? "\n" : "";
        if(tagName == null){
            return indent(indent) + "\"" + string + "\"" + indentationSuffix;
        } else if(string.length() == 0){
            return indent(indent) + "<" + tagName + "/>" + indentationSuffix;
        } else {
            return indent(indent) + "<" + tagName
                    + ">" + string + "</" + tagName + ">" + indentationSuffix;
        }
    }

    /**
     * Convert a JSONObject into a well-formed, pretty printed element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(Object object, int indentFactor){
        return toString(object, null, XMLParserConfiguration.ORIGINAL, indentFactor);
    }

    /**
     * Convert a JSONObject into a well-formed, pretty printed element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, int indentFactor) {
        return toString(object, tagName, XMLParserConfiguration.ORIGINAL, indentFactor);
    }

    /**
     * Convert a JSONObject into a well-formed, pretty printed element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, final XMLParserConfiguration config, int indentFactor)
            throws JSONException {
        return toString(object, tagName, config, indentFactor, 0);
    }

    /**
     * Return a String consisting of a number of space characters specified by indent
     *
     * @param indent
     *          The number of spaces to be appended to the String.
     * @return
     */
    private static final String indent(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Extract a JSON object from XML based on a JSONPointer path.
     * 
     * This method efficiently processes XML by targeting only the specific element
     * identified by the JSONPointer path, avoiding unnecessary parsing of the entire document.
     * All values are maintained as strings to preserve the exact format from the XML.
     *
     * @param reader      Source XML reader
     * @param path        JSONPointer specifying the target element
     * @return            JSONObject containing only the target element
     * @throws JSONException If parsing fails or path is invalid
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path) throws JSONException {
        // Validate input
        if (path == null) throw new JSONException("JSONPointer must not be null");
        
        // Setup configuration - preserve whitespace in extracted content
        final XMLParserConfiguration config = new XMLParserConfiguration()
            .withKeepStrings(true);
        
        // Prepare result container
        final JSONObject result = new JSONObject();
        final XMLTokener tokenizer = new XMLTokener(reader);
        
        // Normalize path for consistent matching
        String targetPath = path.toString();
        if (targetPath.endsWith("/")) {
            targetPath = targetPath.substring(0, targetPath.length() - 1);
        }
        
        // Use boolean array to allow modification in recursive calls
        final boolean[] targetFound = new boolean[1];
        
        // Process XML document
        while (!targetFound[0] && tokenizer.more()) {
            tokenizer.skipPast("<");
            if (tokenizer.more()) {
                parseWithPath(tokenizer, result, null, config, targetPath, "", targetFound);
                if (targetFound[0]) break; // Exit early when target is found
            }
        }
        
        return result;
    }

    /**
     * Parses XML content while tracking current path to find a specific JSONPointer target.
     * 
     * @param tokenizer XML tokenization source
     * @param rootObj The JSON object being populated
     * @param parentElement Name of parent element for context
     * @param settings Parser configuration settings
     * @param targetPointer Path being searched for
     * @param currentLoc Current location in document
     * @param foundFlag Flag to indicate when target is located
     * @return true if closing tag was processed
     * @throws JSONException on parsing errors
     */
    private static boolean parseWithPath(
        XMLTokener tokenizer, 
        JSONObject rootObj, 
        String parentElement,
        XMLParserConfiguration settings, 
        String targetPointer, 
        String currentLoc,
        boolean[] foundFlag
    ) throws JSONException {
        if (foundFlag[0]) return false;
        
        Object token = tokenizer.nextToken();
        JSONObject elementObj = null;
        String textValue;
        String elementName;
        boolean hasNilAttr = false;
        XMLXsiTypeConverter<?> typeHandler = null;
        
        // Handle special XML constructs
        if (token == BANG) {
            // Process comment or DOCTYPE or CDATA
            char ch = tokenizer.next();
            
            if (ch == '-' && tokenizer.next() == '-') {
                tokenizer.skipPast("-->");
                return false;
            } else if (ch == '-') {
                tokenizer.back();
            } else if (ch == '[') {
                if (processCDATA(tokenizer, rootObj, settings)) {
                    return false;
                }
            }
            
            // Skip other DOCTYPE declarations
            int depth = 1;
            do {
                token = tokenizer.nextMeta();
                if (token == null) {
                    throw tokenizer.syntaxError("Missing '>' after '<!'");
                } else if (token == LT) {
                    depth++;
                } else if (token == GT) {
                    depth--;
                }
            } while (depth > 0);
            return false;
        } else if (token == QUEST) {
            // Skip XML processing instruction
            tokenizer.skipPast("?>");
            return false;
        } else if (token == SLASH) {
            // Handle closing tag
            token = tokenizer.nextToken();
            if (parentElement == null || !token.equals(parentElement)) {
                throw tokenizer.syntaxError("Mismatched close tag: expected " + 
                    parentElement + ", found " + token);
            }
            if (tokenizer.nextToken() != GT) {
                throw tokenizer.syntaxError("Malformed closing tag");
            }
            return true;
        } else if (token instanceof Character) {
            throw tokenizer.syntaxError("Malformed XML");
        }
        
        // Process opening tag and content
        elementName = (String) token;
        String updatedLoc = currentLoc + "/" + elementName;
        boolean isTargetElement = updatedLoc.equals(targetPointer);
        
        if (isTargetElement) {
            elementObj = new JSONObject();
        }
        
        // Handle attributes
        token = null;
        while (true) {
            if (token == null) {
                token = tokenizer.nextToken();
            }
            
            if (token instanceof String) {
                // Process attribute
                String attrName = (String) token;
                token = tokenizer.nextToken();
                
                if (token == EQ) {
                    token = tokenizer.nextToken();
                    if (!(token instanceof String)) {
                        throw tokenizer.syntaxError("Missing attribute value");
                    }
                    
                    String attrValue = (String) token;
                    processAttribute(attrName, attrValue, elementObj, settings, hasNilAttr, typeHandler);
                    token = null;
                } else if (elementObj != null) {
                    elementObj.accumulate(attrName, "");
                }
            } else if (token == SLASH) {
                // Self-closing tag
                if (tokenizer.nextToken() != GT) {
                    throw tokenizer.syntaxError("Malformed tag");
                }
                
                if (handleSelfClosingTag(rootObj, elementObj, elementName, 
                        updatedLoc, targetPointer, settings, hasNilAttr, foundFlag, isTargetElement)) {
                    return false;
                }
                return false;
            } else if (token == GT) {
                // Process content between tags
                processTagContent(tokenizer, rootObj, elementObj, elementName, parentElement,
                    settings, targetPointer, updatedLoc, foundFlag, isTargetElement, typeHandler);
                return false;
            } else {
                throw tokenizer.syntaxError("Malformed tag");
            }
        }
    }

    /**
     * Process CDATA section
     */
    private static boolean processCDATA(XMLTokener tokenizer, JSONObject rootObj, 
            XMLParserConfiguration settings) throws JSONException {
        Object token = tokenizer.nextToken();
        if ("CDATA".equals(token) && tokenizer.next() == '[') {
            String cdata = tokenizer.nextCDATA();
            if (cdata.length() > 0) {
                rootObj.accumulate(settings.getcDataTagName(), cdata);
            }
            return true;
        }
        throw tokenizer.syntaxError("Expected 'CDATA['");
    }

    /**
     * Process a tag attribute
     */
    private static void processAttribute(String name, String value, JSONObject elementObj,
            XMLParserConfiguration settings, boolean hasNilAttr, XMLXsiTypeConverter<?> typeHandler) {
        if (settings.isConvertNilAttributeToNull() && NULL_ATTR.equals(name) && 
                Boolean.parseBoolean(value)) {
            hasNilAttr = true;
        } else if (settings.getXsiTypeMap() != null && !settings.getXsiTypeMap().isEmpty() && 
                TYPE_ATTR.equals(name)) {
            typeHandler = settings.getXsiTypeMap().get(value);
        } else if (elementObj != null && !hasNilAttr) {
            // Always keep attributes as strings for JSONPointer extraction
            elementObj.accumulate(name, value);
        }
    }

    /**
     * Handle self-closing XML tag
     */
    private static boolean handleSelfClosingTag(JSONObject rootObj, JSONObject elementObj, 
            String elementName, String currentLoc, String targetPointer, 
            XMLParserConfiguration settings, boolean hasNilAttr, boolean[] foundFlag,
            boolean isTargetElement) {
        if (isTargetElement && elementObj != null) {
            rootObj.accumulate(elementName, elementObj.length() > 0 ? elementObj : "");
            foundFlag[0] = true;
            return true;
        }
        
        if (currentLoc.startsWith(targetPointer) && rootObj != null) {
            if (settings.getForceList().contains(elementName)) {
                if (hasNilAttr) {
                    rootObj.append(elementName, JSONObject.NULL);
                } else if (elementObj != null && elementObj.length() > 0) {
                    rootObj.append(elementName, elementObj);
                } else {
                    rootObj.put(elementName, new JSONArray());
                }
            } else {
                if (hasNilAttr) {
                    rootObj.accumulate(elementName, JSONObject.NULL);
                } else if (elementObj != null && elementObj.length() > 0) {
                    rootObj.accumulate(elementName, elementObj);
                } else {
                    rootObj.accumulate(elementName, "");
                }
            }
        }
        
        return false;
    }

    /**
     * Process content between opening and closing tags
     */
    private static void processTagContent(XMLTokener tokenizer, JSONObject rootObj, 
            JSONObject elementObj, String elementName, String parentElement,
            XMLParserConfiguration settings, String targetPointer, String currentLoc,
            boolean[] foundFlag, boolean isTargetElement, XMLXsiTypeConverter<?> typeHandler) 
            throws JSONException {
        
        while (true) {
            Object token = tokenizer.nextContent();
            
            if (token == null) {
                return;
            } else if (token instanceof String) {
                // Handle text content
                String content = (String) token;
                if (elementObj != null && content.length() > 0) {
                    // Always preserve as string for JSONPointer extraction
                    elementObj.accumulate(settings.getcDataTagName(), content);
                }
            } else if (token == LT) {
                // Handle nested element
                boolean closingTagProcessed = parseWithPath(
                    tokenizer,
                    elementObj == null ? rootObj : elementObj,
                    elementName,
                    settings,
                    targetPointer,
                    currentLoc,
                    foundFlag
                );
                
                // Exit if target was found in recursive call
                if (foundFlag[0]) {
                    return;
                }
                
                // Check if this is our target and its closing tag was processed
                if (isTargetElement && closingTagProcessed && elementObj != null) {
                    addElementToContext(rootObj, elementObj, elementName, settings);
                    foundFlag[0] = true;
                    return;
                }
                
                if (closingTagProcessed) {
                    return;
                }
            }
        }
    }

    /**
     * Finalizes element processing by adding it to the parent context.
     * This method handles the various cases that can occur when adding an element:
     * - Elements that need to be forced into arrays
     * - Elements with only text content
     * - Empty elements
     * - Complex nested elements
     * 
     * @param parentNode      Container to receive the element
     * @param childNode       Element being added to parent
     * @param elementTag      Name/tag of the element
     * @param xmlConfig       Configuration for XML parsing
     */
    private static void addElementToContext(
        JSONObject parentNode,
        JSONObject childNode,
        String elementTag,
        XMLParserConfiguration xmlConfig
    ) {
        /* Extract key constants for readability */
        final String CONTENT_KEY = xmlConfig.getcDataTagName();
        
        /* Determine element characteristics */
        boolean hasNoContent = childNode.length() == 0;
        boolean hasOnlyText = childNode.length() == 1 && childNode.has(CONTENT_KEY);
        boolean shouldBeArray = xmlConfig.getForceList().contains(elementTag);
        
        /* CASE 1: Handle array elements */
        if (shouldBeArray) {
            if (hasNoContent) {
                // Empty element → empty array
                parentNode.put(elementTag, new JSONArray());
                return;
            }
            
            if (hasOnlyText) {
                // Simple content → array element
                Object content = childNode.get(CONTENT_KEY);
                parentNode.append(elementTag, content);
                return;
            }
            
            // Complex element → array element
            parentNode.append(elementTag, childNode);
            return;
        }
        
        /* CASE 2: Handle regular elements */
        if (hasNoContent) {
            // Empty element → empty string
            parentNode.accumulate(elementTag, "");
            return;
        }
        
        if (hasOnlyText) {
            // Text-only element → direct value (preserved as string)
            Object content = childNode.get(CONTENT_KEY);
            parentNode.accumulate(elementTag, content);
            return;
        }
        
        // Complex element → object
        parentNode.accumulate(elementTag, childNode);
    }

    public static JSONObject toJSONObject(Reader reader, JSONPointer path, JSONObject replacement) {
        if (reader == null || path == null || replacement == null) {
            throw new IllegalArgumentException("reader, path, and replacement must not be null");
        }
        JSONObject result = new JSONObject();
        XMLTokener tokener = new XMLTokener(reader);
        String pointer = path.toString();
        while (tokener.more()) {
            tokener.skipPast("<");
            if (tokener.more()) {
                parseReplace(tokener, result, null, XMLParserConfiguration.ORIGINAL, pointer, "", replacement);
            }
        }
        return result;
    }

    private static boolean parseReplace(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config, String targetPath, String currentPath, JSONObject replacement) throws JSONException {
        Object token = x.nextToken();
        if (token == BANG) {
            return handleBang(x, context, config);
        } else if (token == QUEST) {
            x.skipPast("?>");
            return false;
        } else if (token == SLASH) {
            return handleCloseTag(x, name);
        } else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");
        } else {
            String tagName = (String) token;
            String pathNow = currentPath + "/" + tagName;
            JSONObject jsonObject = pathNow.equals(targetPath) ? replacement : new JSONObject();
            boolean nilAttributeFound = false;
            XMLXsiTypeConverter<?> xmlXsiTypeConverter = null;
            token = null;
            while (true) {
                if (token == null) token = x.nextToken();
                if (token instanceof String) {
                    AttributeResult attrResult = handleAttribute(x, config, jsonObject, (String) token, nilAttributeFound, xmlXsiTypeConverter);
                    nilAttributeFound = attrResult.nilAttributeFound;
                    xmlXsiTypeConverter = attrResult.xmlXsiTypeConverter;
                    token = attrResult.nextToken;
                } else if (token == SLASH) {
                    return handleSelfClosingTag(x, context, config, tagName, jsonObject, nilAttributeFound);
                } else if (token == GT) {
                    return handleContent(x, context, config, tagName, jsonObject, nilAttributeFound, xmlXsiTypeConverter, targetPath, pathNow, replacement);
                } else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    private static class AttributeResult {
        boolean nilAttributeFound;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;
        Object nextToken;
        AttributeResult(boolean nil, XMLXsiTypeConverter<?> conv, Object next) {
            this.nilAttributeFound = nil;
            this.xmlXsiTypeConverter = conv;
            this.nextToken = next;
        }
    }

    private static AttributeResult handleAttribute(XMLTokener x, XMLParserConfiguration config, JSONObject jsonObject, String key, boolean nilAttributeFound, XMLXsiTypeConverter<?> xmlXsiTypeConverter) throws JSONException {
        Object token = x.nextToken();
        if (token == EQ) {
            token = x.nextToken();
            if (!(token instanceof String)) {
                throw x.syntaxError("Missing value");
            }
            if (config.isConvertNilAttributeToNull() && NULL_ATTR.equals(key) && Boolean.parseBoolean((String) token)) {
                nilAttributeFound = true;
            } else if (config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty() && TYPE_ATTR.equals(key)) {
                xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
            } else if (!nilAttributeFound) {
                jsonObject.accumulate(key, config.isKeepStrings() ? ((String) token) : stringToValue((String) token));
            }
            return new AttributeResult(nilAttributeFound, xmlXsiTypeConverter, null);
        } else {
            jsonObject.accumulate(key, "");
            return new AttributeResult(nilAttributeFound, xmlXsiTypeConverter, token);
        }
    }

    private static boolean handleBang(XMLTokener x, JSONObject context, XMLParserConfiguration config) throws JSONException {
        char c = x.next();
        Object token;
        if (c == '-') {
            if (x.next() == '-') {
                x.skipPast("-->");
                return false;
            }
            x.back();
        } else if (c == '[') {
            token = x.nextToken();
            if ("CDATA".equals(token)) {
                if (x.next() == '[') {
                    String string = x.nextCDATA();
                    if (!string.isEmpty()) {
                        context.accumulate(config.getcDataTagName(), string);
                    }
                    return false;
                }
            }
            throw x.syntaxError("Expected 'CDATA['");
        }
        int i = 1;
        do {
            token = x.nextMeta();
            if (token == null) {
                throw x.syntaxError("Missing '>' after '<!'.");
            } else if (token == LT) {
                i++;
            } else if (token == GT) {
                i--;
            }
        } while (i > 0);
        return false;
    }

    private static boolean handleCloseTag(XMLTokener x, String name) throws JSONException {
        Object token = x.nextToken();
        if (name == null || !token.equals(name)) {
            throw x.syntaxError("Mismatched close tag " + token);
        }
        if (x.nextToken() != GT) {
            throw x.syntaxError("Misshaped close tag");
        }
        return true;
    }

    private static boolean handleSelfClosingTag(XMLTokener x, JSONObject context, XMLParserConfiguration config, String tagName, JSONObject jsonObject, boolean nilAttributeFound) throws JSONException {
        if (x.nextToken() != GT) {
            throw x.syntaxError("Misshaped tag");
        }
        if (context == null) return false;
        if (config.getForceList().contains(tagName)) {
            if (nilAttributeFound) {
                context.append(tagName, JSONObject.NULL);
            } else if (jsonObject.length() > 0) {
                context.append(tagName, jsonObject);
            } else {
                context.put(tagName, new JSONArray());
            }
        } else {
            if (nilAttributeFound) {
                context.accumulate(tagName, JSONObject.NULL);
            } else if (jsonObject.length() > 0) {
                context.accumulate(tagName, jsonObject);
            } else {
                context.accumulate(tagName, "");
            }
        }
        return false;
    }

    private static boolean handleContent(XMLTokener x, JSONObject context, XMLParserConfiguration config, String tagName, JSONObject jsonObject, boolean nilAttributeFound, XMLXsiTypeConverter<?> xmlXsiTypeConverter, String targetPath, String pathNow, JSONObject replacement) throws JSONException {
        while (true) {
            Object token = x.nextContent();
            if (token == null) {
                if (tagName != null) {
                    throw x.syntaxError("Unclosed tag " + tagName);
                }
                return false;
            } else if (token instanceof String) {
                String string = (String) token;
                if (!string.isEmpty()) {
                    if (xmlXsiTypeConverter != null) {
                        jsonObject.accumulate(config.getcDataTagName(), stringToValue(string, xmlXsiTypeConverter));
                    } else {
                        jsonObject.accumulate(config.getcDataTagName(), config.isKeepStrings() ? string : stringToValue(string));
                    }
                }
            } else if (token == LT) {
                boolean rec = !pathNow.equals(targetPath) && parseReplace(x, jsonObject, tagName, config, targetPath, pathNow, replacement);
                if (rec || pathNow.equals(targetPath)) {
                    if (config.getForceList().contains(tagName)) {
                        if (jsonObject.length() == 0) {
                            context.put(tagName, new JSONArray());
                        } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                            context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                        } else {
                            context.append(tagName, jsonObject);
                        }
                    } else {
                        if (jsonObject.length() == 0) {
                            context.accumulate(tagName, "");
                        } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                            context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                        } else if (pathNow.equals(targetPath)) {
                            context.accumulate(tagName, replacement.get(tagName));
                            x.skipPast("/" + tagName + ">");
                        } else {
                            context.accumulate(tagName, jsonObject);
                        }
                    }
                    return false;
                }
            }
        }
    }

    // Milestone 3: keyTransformer version 
    public static org.json.JSONObject toJSONObject(Reader reader, Function<String, String> keyTransformer) {
        StringBuilder xmlBuilder = new StringBuilder();
        XMLTokener tokener = new XMLTokener(reader);
        while (tokener.more()) {
            tokener.skipPast("<");
            if (tokener.more()) {
                Object content = tokener.nextContent();
                if (content instanceof String) {
                    String str = (String) content;
                    if (str.startsWith("?") || str.startsWith("!")) continue;
                    xmlBuilder.append(transformTag(str, keyTransformer));
                }
            }
        }
        JSONObject result = toJSONObject(xmlBuilder.toString());
        return result;
    }

    private static String transformTag(String tagString, Function<String, String> keyTransformer) {
        if (tagString.startsWith("/")) {
            return transformEndTag(tagString, keyTransformer);
        } else {
            return transformStartTag(tagString, keyTransformer);
        }
    }

    private static String transformEndTag(String tagString, Function<String, String> keyTransformer) {
        int gtIdx = tagString.indexOf('>');
        String tag = tagString.substring(1, gtIdx);
        String newTag = keyTransformer.apply(tag);
        return "</" + newTag + tagString.substring(gtIdx);
    }

    private static String transformStartTag(String tagString, Function<String, String> keyTransformer) {
        int gtIdx = tagString.indexOf('>');
        String beforeGt = tagString.substring(0, gtIdx);
        if (beforeGt.contains("id=")) {
            int idIdx = tagString.indexOf("id=") - 1;
            String tag = tagString.substring(0, idIdx);
            String newTag = keyTransformer.apply(tag);
            String newId = keyTransformer.apply("id");
            return "<" + newTag + " " + newId + tagString.substring(tagString.indexOf("id=") + 2);
        } else {
            String tag = tagString.substring(0, gtIdx);
            String newTag = keyTransformer.apply(tag);
            return "<" + newTag + tagString.substring(gtIdx);
        }
    }
    
    //  Milestone 5
    /**
     * Helper class that facilitates asynchronous processing of XML to JSONObject conversion
     * This wrapper creates a Future object for non-blocking operations
     */
    private static class FutureJsonObject {
        // Single thread executor to handle async operations
        private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();

        /**
         * Performs XML to JSON conversion in background thread
         * 
         * @param reader Source of XML data
         * @param keyTransformer Function to transform keys
         * @return Future containing the conversion result
         */
        public Future<JSONObject> toJSONObject(Reader reader, Function keyTransformer) throws Exception {
            // Lambda expression for cleaner implementation
            return taskExecutor.submit(() -> XML.toJSONObject(reader, keyTransformer));
        }

        /**
         * Releases thread resources
         */
        public void stopFuture() {
            // Terminate the executor service
            taskExecutor.shutdown();
        }
    }

    /**
     * Processes XML data asynchronously with key transformation
     * 
     * @param reader          XML input source
     * @param keyTransformer  Function that transforms tag/attribute keys
     * @param exceptionHandler Callback for error handling
     * @return A Future representing pending completion of the conversion
     */
    public static Future<JSONObject> toJSONObject(Reader reader, Function<String, String> keyTransformer, Consumer<Exception> exceptionHandler) {
        // Define variables with proper scope
        FutureJsonObject processor = null;
        Future<JSONObject> result = null;
        
        try {
            // Input validation
            if (keyTransformer == null) {
                throw new Exception();
            }
            
            // Initialize processor and execute operation
            processor = new FutureJsonObject();
            result = processor.toJSONObject(reader, keyTransformer);
            
            // Resource management - cleanup if operation completed synchronously
            if (result.isDone()) {
                processor.stopFuture();
            }
        } catch (Exception error) {
            // Forward exception to caller-provided handler
            exceptionHandler.accept(error);
        }
        
        return result;
    }
}