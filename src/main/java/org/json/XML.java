package org.json;

/*
Public Domain.
*/

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;

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
     * @param config Configuration options for the parser.
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
     * Convert a well-formed (but not necessarily valid) XML into a JSONObject
     * and extract the sub-object specified by the JSONPointer path.
     *
     * @param reader The XML source reader.
     * @param path The JSONPointer specifying the sub-object to extract.
     * @return A JSONObject containing the sub-object specified by the path.
     * @throws JSONException Thrown if there is an error parsing the string
     *                       or if the path does not exist.
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path) throws JSONException {
        if (reader == null) {
            throw new JSONException("Reader must not be null");
        }
        if (path == null) {
            throw new NullPointerException("JSONPointer must not be null");
        }

        XMLTokener x = new XMLTokener(reader);
        JSONObject root = new JSONObject();
        String[] pathSegments = path.toString().split("/");

        while (x.more()) {
            x.skipPast("<");
            if (x.more()) {
                if (parseToPath(x, root, pathSegments, 0)) {
                    Object result = root.query(path);
                    if (result instanceof JSONObject) {
                        return (JSONObject) result;
                    } else {
                        throw new JSONException("Path does not point to a JSONObject: " + path.toString());
                    }
                }
            }
        }

        throw new JSONException("Path not found: " + path.toString());
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a JSONObject,
     * replace the sub-object specified by the JSONPointer path with the provided
     * replacement JSONObject, and return the modified JSONObject.
     *
     * @param reader The XML source reader.
     * @param path The JSONPointer specifying the sub-object to replace.
     * @param replacement The JSONObject to replace the sub-object with.
     * @return A JSONObject containing the modified structure.
     * @throws JSONException Thrown if there is an error parsing the string
     *                       or if the path does not exist.
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path, JSONObject replacement) throws JSONException {
        if (reader == null) {
            throw new JSONException("Reader must not be null");
        }
        if (path == null) {
            throw new NullPointerException("JSONPointer must not be null");
        }
        if (replacement == null) {
            throw new JSONException("Replacement JSONObject must not be null");
        }

        XMLTokener x = new XMLTokener(reader);
        JSONObject root = new JSONObject();
        String[] pathSegments = path.toString().split("/");

        while (x.more()) {
            x.skipPast("<");
            if (x.more()) {
                if (parseAndReplaceToPath(x, root, pathSegments, 0, replacement)) {
                    return root;
                }
            }
        }

        throw new JSONException("Path not found: " + path.toString());
    }

    private static boolean parseToPath(XMLTokener x, JSONObject context, String[] pathSegments, int pathIndex) throws JSONException {
        if (pathIndex >= pathSegments.length) {
            return false;
        }

        String currentSegment = pathSegments[pathIndex];
        String tagName;
        Object token = x.nextToken();

        if (token instanceof String) {
            tagName = (String) token;
            if (currentSegment.equals(tagName)) {
                JSONObject child = new JSONObject();
                context.put(tagName, child);
                if (pathIndex == pathSegments.length - 1) {
                    // Successfully reached the target path
                    return true;
                }
                while (x.more()) {
                    if (parseToPath(x, child, pathSegments, pathIndex + 1)) {
                        return true;
                    }
                }
            } else {
                // Skip unrelated tags
                x.skipPast("</" + tagName + ">");
            }
        } else if (token == XML.SLASH) {
            // Handle self-closing tags
            token = x.nextToken();
            if (token instanceof String && currentSegment.equals(token)) {
                if (pathIndex == pathSegments.length - 1) {
                    context.put((String) token, new JSONObject());
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean parseAndReplaceToPath(XMLTokener x, JSONObject context, String[] pathSegments, int pathIndex, JSONObject replacement) throws JSONException {
        if (pathIndex >= pathSegments.length) {
            return false;
        }

        String currentSegment = pathSegments[pathIndex];
        String tagName;
        Object token = x.nextToken();

        if (token instanceof String) {
            tagName = (String) token;
            if (currentSegment.equals(tagName)) {
                if (pathIndex == pathSegments.length - 1) {
                    // Replace the sub-object at the specified path
                    if (context.has(tagName) && context.get(tagName) instanceof JSONArray) {
                        JSONArray array = context.getJSONArray(tagName);
                        int index = Integer.parseInt(pathSegments[pathIndex + 1]);
                        array.put(index, replacement);
                    } else {
                        context.put(tagName, replacement);
                    }
                    x.skipPast("</" + tagName + ">");
                    return true;
                }
                JSONObject child = new JSONObject();
                context.put(tagName, child);
                while (x.more()) {
                    if (parseAndReplaceToPath(x, child, pathSegments, pathIndex + 1, replacement)) {
                        return true;
                    }
                }
            } else {
                // Skip unrelated tags
                x.skipPast("</" + tagName + ">");
            }
        } else if (token == XML.SLASH) {
            // Handle self-closing tags
            token = x.nextToken();
            if (token instanceof String && currentSegment.equals(token)) {
                if (pathIndex == pathSegments.length - 1) {
                    context.put((String) token, replacement);
                    return true;
                }
            }
        }
        return false;
    }


    /**     =============================================== */
// ...existing code...

public static JSONObject toJSONObject2(Reader reader, JSONPointer path) throws JSONException {
    if (reader == null) {
        throw new JSONException("Reader must not be null");
    }
    if (path == null) {
        throw new NullPointerException("JSONPointer must not be null");
    }

    // 將 JSONPointer 字串前後的斜線去除後再切割
    String pathStr = path.toString();
    if (pathStr.startsWith("/")) {
        pathStr = pathStr.substring(1);
    }
    if (pathStr.endsWith("/")) {
        pathStr = pathStr.substring(0, pathStr.length() - 1);
    }
    String[] segments = pathStr.split("/");

    XMLTokener x = new XMLTokener(reader);
    JSONObject result = new JSONObject();

    // 跳過 XML 聲明
    x.skipPast("?>");

    // 使用 parseWithPath 進行局部解析
    if (parseWithPath(x, result, segments, 0)) {
        return result;
    }
    throw new JSONException("Path not found: " + path.toString());
}

// ...existing code...

private static boolean parseWithPath(XMLTokener x, JSONObject context, String[] pathSegments, int pathIndex) throws JSONException {
    // 1. 跳過非字串 token，直到抓到標籤名稱或 EOF
    Object token = x.nextToken();
    while (token != null && !(token instanceof String)) {
        if (token instanceof Character) {
            char c = (Character) token;
            if (c == BANG || c == QUEST) {
                x.skipPast(">");
            }
        }
        token = x.nextToken();
    }
    if (token == null) {
        return false;
    }

    // 2. 判定是否符合要找的標籤
    String tagName = (String) token;
    if (!tagName.equals(pathSegments[pathIndex])) {
        // 若不符合，整段跳過即可
        skipTag(x, tagName);
        return false;
    }

    // 3. 若符合，建立 JSON 子物件
    JSONObject child = new JSONObject();
    context.put(tagName, child);

    // 4. 消費可能的屬性，直到看見 '>' or '/>'
    token = x.nextToken();
    while (token != XML.GT && token != XML.SLASH) {
        // 跳過所有字串（屬性名稱/值）及 '=' 符號
        token = x.nextToken();
    }

    // 5. 處理自閉合標籤
    if (token == XML.SLASH) {
        if (x.nextToken() != XML.GT) {
            throw new JSONException("Malformed self-closing tag for " + tagName);
        }
        return (pathIndex == pathSegments.length - 1);
    }

    // 6. 此時 token == '>', 進入內文或子標籤的處理
    if (pathIndex == pathSegments.length - 1) {
        // 若已經到最後一段，就消耗內容直到對應的 </tagName>
        while (true) {
            token = x.nextContent();
            if (token == null) {
                return false;
            }
            if (token == XML.LT) {
                Object next = x.nextToken();
                if (next == XML.SLASH) {
                    // 檢查關閉標籤
                    if (!tagName.equals(x.nextToken()) || x.nextToken() != XML.GT) {
                        throw new JSONException("Malformed closing tag");
                    }
                    return true;
                } else {
                    // 不是目標標籤，一律跳過
                    skipTag(x, (String) next);
                    return false;
                }
            }
        }
    }

    // 7. 尚未到達最後一段，繼續向下搜尋
    while (true) {
        token = x.nextContent();
        if (token == null) {
            return false;
        }
        if (token == XML.LT) {
            Object next = x.nextToken();
            if (next == XML.SLASH) {
                // 關閉標籤
                if (!tagName.equals(x.nextToken()) || x.nextToken() != XML.GT) {
                    throw new JSONException("Malformed closing tag");
                }
                return false;
            } else {
                // 檢查是否符合下一段路徑
                String childTag = (String) next;
                if ((pathIndex + 1) < pathSegments.length && childTag.equals(pathSegments[pathIndex + 1])) {
                    // 建立孫物件，並嘗試遞迴搜尋
                    JSONObject grandChild = new JSONObject();
                    child.put(childTag, grandChild);

                    // 處理可能的屬性或自閉合
                    token = x.nextToken();
                    while (token != XML.GT && token != XML.SLASH) {
                        token = x.nextToken();
                    }
                    if (token == XML.SLASH) {
                        if (x.nextToken() != XML.GT) {
                            throw new JSONException("Malformed self-closing tag for " + childTag);
                        }
                        if (pathIndex + 1 == pathSegments.length - 1) {
                            return true;
                        }
                        continue;
                    }
                    // 走遞迴
                    if (parseWithPath(x, grandChild, pathSegments, pathIndex + 1)) {
                        return true;
                    } else {
                        skipTag(x, childTag);
                    }
                } else {
                    // 路徑不符，跳過
                    skipTag(x, childTag);
                }
            }
        }
    }
}

// ...existing code...
private static void skipTag(XMLTokener x, String tagName) throws JSONException {
    System.out.println("開始跳過標籤: " + tagName);
    
    // 1. 首先確保消耗屬性直到 > 或 />
    Object token = x.nextToken();
    while (token != GT && token != SLASH) {
        token = x.nextToken();
    }
    
    // 2. 處理自閉合標籤 <tag/>
    if (token == SLASH) {
        if (x.nextToken() != GT) {
            throw new JSONException("Malformed self-closing tag for " + tagName);
        }
        System.out.println("跳過自閉合標籤: " + tagName);
        return;
    }
    
    // 3. 消耗內容直到完全匹配的閉合標籤 </tagName>
    int depth = 1; // 標籤嵌套深度

    // 關鍵調整：直接使用 skipPast 來更可靠地跳過整個標籤
    try {
        x.skipPast("</" + tagName + ">");
    } catch (JSONException e) {
        System.out.println("無法直接跳過，改用手動跳過: " + tagName);
        
        // 備用方案：手動跳過所有內容直到找到匹配的閉合標籤
        while (depth > 0 && x.more()) {
            token = x.nextContent();
            if (token == null) {
                break;
            }
            if (token == LT) {
                token = x.nextToken();
                if (token == SLASH) {
                    String closingTag = (String) x.nextToken();
                    if (closingTag.equals(tagName)) {
                        depth--;
                        x.nextToken(); // 消耗 '>'
                    }
                } else if (token instanceof String) {
                    // 遇到新子標籤
                    String subTag = (String) token;
                    if (subTag.equals(tagName)) {
                        depth++; // 相同標籤嵌套
                    }
                    
                    // 消耗子標籤的屬性
                    token = x.nextToken();
                    while (token != GT && token != SLASH) {
                        token = x.nextToken();
                    }
                    
                    if (token == SLASH) {
                        x.nextToken(); // 消耗自閉合標籤的 '>'
                    }
                }
            }
        }
    }
    
    System.out.println("已跳過標籤: " + tagName);
}
}