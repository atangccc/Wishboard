package cn.aobp.wishboard;

import java.lang.reflect.Method;
import java.util.Map;

/** Reads Halo setting JSON without coupling to a Jackson class loader. */
final class SettingValues {
    private SettingValues() {
    }

    static Object path(Object node, String key) {
        if (node == null) {
            return null;
        }
        if (node instanceof Map<?, ?> map) {
            return map.get(key);
        }
        try {
            Method method = node.getClass().getMethod("path", String.class);
            return method.invoke(node, key);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static String text(Object node, String key, String fallback) {
        Object value = path(node, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String string) {
            return string.isEmpty() ? fallback : string;
        }
        Object result = invoke(value, "asText", fallback);
        String text = result == null ? "" : result.toString();
        return text.isEmpty() ? fallback : text;
    }

    static boolean bool(Object node, String key, boolean fallback) {
        Object value = path(node, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        Object result = invoke(value, "asBoolean", fallback);
        return result instanceof Boolean bool ? bool : fallback;
    }

    static int integer(Object node, String key, int fallback) {
        Object value = path(node, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        Object result = invoke(value, "asInt", fallback);
        return result instanceof Number number ? number.intValue() : fallback;
    }

    private static Object invoke(Object target, String name, Object fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) {
                    return method.invoke(target, fallback);
                }
            }
            return fallback;
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }
}
