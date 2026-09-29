package com.example.item;

import com.example.item.ItemSearchCondition.SortKey;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스. 레거시 search.php 의 파라미터 해석을 그대로 옮겼다.
 * 없는 단원 · 범위 밖 난이도도 오류가 아니라 조건 그대로 조회한다(대개 0건).
 * 문자열 처리는 PHP 7.4 동작(trim 대상 문자, (int) 변환, ASCII 소문자화, 코드포인트 길이)에 맞춘다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    private static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_TAG_LENGTH = 50;
    private static final int MAX_PAGE = 999;

    /** PHP trim() 이 지우는 문자. Java trim() 과 달리 \f 등은 지우지 않는다. */
    private static final String PHP_TRIM_CHARS = " \t\n\r\0\u000B";

    /** PCRE 의 $ 처럼 끝의 \n 한 개 앞에서도 맞도록 UNIX_LINES + find() 로 쓴다. */
    private static final Pattern LEVEL_PATTERN = Pattern.compile("^[1-5]$", Pattern.UNIX_LINES);
    private static final Pattern PAGE_PATTERN = Pattern.compile("^[0-9]+$", Pattern.UNIX_LINES);

    /** PHP (int) 변환이 읽는 앞부분: 공백, 부호, 숫자(소수 · 지수 포함). */
    private static final Pattern PHP_NUMERIC_PREFIX =
        Pattern.compile("[ \\t\\n\\r\\u000B\\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

    private final ItemSearchRepository itemSearchRepository;

    public ItemSearchService(ItemSearchRepository itemSearchRepository) {
        this.itemSearchRepository = itemSearchRepository;
    }

    /** 문항 검색. 전체 건수가 0이면 안내 문구를 함께 돌려준다. */
    public ItemSearchResponse search(Map<String, List<String>> params) {
        ItemSearchCondition condition = toCondition(params);
        long total = itemSearchRepository.count(condition);
        List<ItemSearchRow> rows = itemSearchRepository.findPage(condition);
        log.debug("item search matched {} items, page {}", total, condition.page());
        return new ItemSearchResponse(rows, total, total == 0 ? NO_RESULT_MESSAGE : null);
    }

    private static ItemSearchCondition toCondition(Map<String, List<String>> params) {
        String sort = asciiLower(phpTrim(param(params, "sort", "")));
        String dir = asciiLower(phpTrim(param(params, "dir", "")));
        if (!dir.equals("asc") && !dir.equals("desc")) {
            dir = "";
        }
        SortKey sortKey = sortKey(sort);
        return new ItemSearchCondition(
            keyword(param(params, "q", "")),
            emptyToNull(phpTrim(param(params, "unit", ""))),
            level(param(params, "level", "")),
            tag(param(params, "tag", "")),
            sortKey,
            descending(sortKey, dir),
            page(param(params, "page", "1")));
    }

    /** 파라미터 값. {@code name[]} 배열이면 첫 값, 같은 이름이 반복되면 마지막 값(PHP $_GET 과 같다). */
    private static String param(Map<String, List<String>> params, String name, String defaultValue) {
        List<String> arrayValues = params.get(name + "[]");
        if (arrayValues != null && !arrayValues.isEmpty()) {
            return arrayValues.get(0);
        }
        List<String> values = params.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(values.size() - 1);
        }
        return defaultValue;
    }

    private static String keyword(String raw) {
        return emptyToNull(cutCodePoints(phpTrim(raw), MAX_KEYWORD_LENGTH));
    }

    /** 빈 값이면 {@code null}(기본 필터), 1~5 한 자리면 그 값, 그 밖에는 PHP (int) 변환값. */
    private static Long level(String raw) {
        String level = phpTrim(raw);
        if (level.isEmpty()) {
            return null;
        }
        if (LEVEL_PATTERN.matcher(level).find()) {
            return Long.valueOf(level);
        }
        return phpIntval(level);
    }

    private static String tag(String raw) {
        String tag = phpTrim(raw);
        if (tag.isEmpty()) {
            return null;
        }
        return cutCodePoints(tag, MAX_TAG_LENGTH);
    }

    private static SortKey sortKey(String sort) {
        return switch (sort) {
            case "id" -> SortKey.ID;
            case "title" -> SortKey.TITLE;
            case "unit" -> SortKey.UNIT;
            case "level" -> SortKey.LEVEL;
            case "created" -> SortKey.CREATED;
            default -> SortKey.DEFAULT;
        };
    }

    /** 방향이 비어 있으면 id · title · unit 은 오름차순, level · created 는 내림차순. */
    private static boolean descending(SortKey sortKey, String dir) {
        if (dir.isEmpty()) {
            return sortKey == SortKey.LEVEL || sortKey == SortKey.CREATED;
        }
        return dir.equals("desc");
    }

    /** 숫자만이면 그 값(0 이하는 1, 999 초과는 999), 그 밖에는 1. 레거시처럼 trim 하지 않는다. */
    private static int page(String raw) {
        long page = 1;
        if (PAGE_PATTERN.matcher(raw).find()) {
            page = phpIntval(raw);
        }
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
        }
        return (int) page;
    }

    /** PHP 7.4 의 (int) 문자열 변환. 앞부분 숫자만 읽고(없으면 0), 소수는 버리고, 범위를 넘으면 long 끝값. */
    private static long phpIntval(String value) {
        Matcher matcher = PHP_NUMERIC_PREFIX.matcher(value);
        if (!matcher.lookingAt()) {
            return 0;
        }
        // double → long 캐스트는 0 쪽으로 버리고 범위 밖은 끝값으로 고정한다(PHP 결과와 같다)
        return (long) Double.parseDouble(matcher.group(1));
    }

    private static String phpTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && PHP_TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && PHP_TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /** PHP strtolower(C 로케일)처럼 ASCII 대문자만 소문자로 바꾼다. */
    private static String asciiLower(String value) {
        StringBuilder lowered = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            lowered.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return lowered.toString();
    }

    /** mb_substr 처럼 코드포인트 기준으로 앞 {@code max} 자만 남긴다. */
    private static String cutCodePoints(String value, int max) {
        if (value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
