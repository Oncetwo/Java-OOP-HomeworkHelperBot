package bot.schedule;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Очень простой "парсер" JSON специализированный под API URFU.
 * Он не универсален, но достаточно надёжен, если структура — массив объектов.
 */
public class JsonSimpleParser {

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{[^\\}]*\\}");

    public static List<Map<String,String>> parseArrayOfObjects(String json) {
        List<Map<String,String>> result = new ArrayList<>();
        Matcher m = OBJECT_PATTERN.matcher(json);
        while (m.find()) {
            String obj = m.group();
            Map<String,String> map = new HashMap<>();
            // find "key": value or "key": "value"
            // simple pattern for string values
            Pattern kvStr = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
            Matcher m2 = kvStr.matcher(obj);
            while (m2.find()) {
                map.put(m2.group(1), m2.group(2));
            }
            // pattern for numeric / null values
            Pattern kvNum = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+|null)");
            Matcher m3 = kvNum.matcher(obj);
            while (m3.find()) {
                map.put(m3.group(1), m3.group(2));
            }
            result.add(map);
        }
        return result;
    }

    public static String getString(Map<String,String> obj, String key) {
        return obj.containsKey(key) ? obj.get(key) : null;
    }

    public static Long getLong(Map<String,String> obj, String key) {
        String v = obj.get(key);
        if (v == null) return null;
        if (v.equals("null")) return null;
        try { return Long.parseLong(v); } catch (Exception e) { return null; }
    }

    public static Integer getInt(Map<String,String> obj, String key) {
        String v = obj.get(key);
        if (v == null) return null;
        try { return Integer.parseInt(v); } catch (Exception e) { return null; }
    }
}
