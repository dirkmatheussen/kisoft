package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.ODataCollectionResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal OData v4 query support for mock read endpoints: {@code $filter}, {@code $top},
 * {@code $skip}, and {@code $count}.
 */
public final class ODataQuerySupport {

    private static final Pattern EQ_FILTER = Pattern.compile(
            "([\\w.]+)\\s+eq\\s+'([^']*)'", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_TOP = 100;
    private static final int MAX_TOP = 1000;

    private ODataQuerySupport() {}

    public static Map<String, String> parseFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return Map.of();
        }
        Map<String, String> conditions = new LinkedHashMap<>();
        for (String part : filter.split("\\s+and\\s+", -1)) {
            Matcher matcher = EQ_FILTER.matcher(part.trim());
            if (matcher.matches()) {
                conditions.put(matcher.group(1), matcher.group(2));
            }
        }
        return conditions;
    }

    public static int parseTop(String top) {
        if (top == null || top.isBlank()) {
            return DEFAULT_TOP;
        }
        try {
            int value = Integer.parseInt(top.trim());
            if (value < 0) {
                return DEFAULT_TOP;
            }
            return Math.min(value, MAX_TOP);
        } catch (NumberFormatException ex) {
            return DEFAULT_TOP;
        }
    }

    public static int parseSkip(String skip) {
        if (skip == null || skip.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(skip.trim());
            return Math.max(value, 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static boolean parseCount(String count) {
        return count != null && "true".equalsIgnoreCase(count.trim());
    }

    public static <T> ODataCollectionResponse<T> buildPage(
            String context,
            List<T> source,
            Map<String, String> filters,
            Function<T, Map<String, String>> fieldValues,
            int top,
            int skip,
            boolean includeCount) {
        List<T> matched = new ArrayList<>();
        for (T item : source) {
            if (matches(filters, fieldValues.apply(item))) {
                matched.add(item);
            }
        }
        int total = matched.size();
        int from = Math.min(skip, total);
        int to = Math.min(from + top, total);
        List<T> page = matched.subList(from, to);
        return new ODataCollectionResponse<>(
                context,
                includeCount ? total : null,
                List.copyOf(page));
    }

    private static boolean matches(Map<String, String> filters, Map<String, String> values) {
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String actual = values.get(filter.getKey());
            if (actual == null || !actual.equals(filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a JPA {@link Specification} of {@code AND}-combined equality predicates from parsed OData
     * {@code $filter} conditions, so filtering and paging happen in the database instead of in memory.
     * {@code fieldToAttribute} maps each supported OData field name to its entity attribute; a condition on
     * an unmapped field yields a never-matching predicate, preserving the empty-result behaviour of the
     * previous in-memory filter. String comparison is used so mixed-type columns (e.g. integer quantity)
     * match the string form parsed from the filter.
     */
    public static <T> Specification<T> equalitySpecification(
            Map<String, String> filters, Map<String, String> fieldToAttribute) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Map.Entry<String, String> condition : filters.entrySet()) {
                String attribute = fieldToAttribute.get(condition.getKey());
                if (attribute == null) {
                    predicates.add(cb.disjunction());
                    continue;
                }
                predicates.add(cb.equal(root.get(attribute).as(String.class), condition.getValue()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static String metadataContext(String basePath, String entitySet) {
        String path = basePath == null || basePath.isBlank() ? "" : basePath.replaceAll("/$", "");
        return path + "/oneapi/v1/$metadata#" + entitySet;
    }

    public static Map<String, String> fields(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("fields() requires key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = pairs[i];
            String value = pairs[i + 1];
            map.put(key, value == null ? "" : value);
        }
        return map;
    }

    public static String str(String value) {
        return value == null ? "" : value;
    }

    public static String str(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
