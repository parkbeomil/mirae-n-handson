package com.example.item;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 문항 검색 저장소. 레거시 search.php 와 같이 공개 문항 뷰 {@code v_item_public} 을 조회한다.
 * 조건 값은 모두 바인딩하고, ORDER BY 는 정해진 문자열 중에서만 고른다.
 */
@Repository
public class ItemSearchRepository {

    /** 한 페이지 행 수. */
    private static final int PAGE_SIZE = 20;

    /** 난이도 입력이 비었을 때 이 값 미만만 조회한다(레거시 동작 그대로 — 난이도 5가 빠진다). */
    private static final int DEFAULT_LEVEL_EXCLUSIVE_UPPER = 5;

    private static final String FROM = " FROM v_item_public";

    private static final String DEFAULT_ORDER_BY = " ORDER BY level DESC, id ASC";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ItemSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 조건에 맞는 전체 건수. */
    public long count(ItemSearchCondition condition) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*)" + FROM + where(condition, params);
        Long total = jdbcTemplate.queryForObject(sql, params, Long.class);
        return total == null ? 0 : total;
    }

    /** 조건에 맞는 행 중 해당 페이지. */
    public List<ItemSearchRow> findPage(ItemSearchCondition condition) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        int offset = (condition.page() - 1) * PAGE_SIZE;
        String sql = "SELECT id, title, unit_code, level, tag_names" + FROM + where(condition, params)
            + orderBy(condition) + " LIMIT " + PAGE_SIZE + " OFFSET " + offset;
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ItemSearchRow(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("unit_code"),
            rs.getInt("level"),
            splitTags(rs.getString("tag_names"))));
    }

    private static String where(ItemSearchCondition condition, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (condition.keyword() != null) {
            // % 와 _ 는 이스케이프하지 않는다(레거시 동작 그대로 — 와일드카드로 동작)
            where.append(" AND (title LIKE :keyword OR stem LIKE :keyword)");
            params.addValue("keyword", "%" + condition.keyword() + "%");
        }
        if (condition.unitCode() != null) {
            where.append(" AND unit_code = :unitCode");
            params.addValue("unitCode", condition.unitCode());
        }
        if (condition.level() == null) {
            where.append(" AND level < ").append(DEFAULT_LEVEL_EXCLUSIVE_UPPER);
        } else {
            where.append(" AND level = :level");
            params.addValue("level", condition.level());
        }
        if (condition.tag() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id")
                .append(" WHERE it.item_id = v_item_public.id AND t.name = :tag)");
            params.addValue("tag", condition.tag());
        }
        return where.toString();
    }

    private static String orderBy(ItemSearchCondition condition) {
        String dir = condition.descending() ? "DESC" : "ASC";
        return switch (condition.sortKey()) {
            case ID -> " ORDER BY id " + dir;
            case TITLE -> " ORDER BY title " + dir + ", id ASC";
            case UNIT -> " ORDER BY unit_code " + dir + ", level DESC, id ASC";
            case LEVEL -> " ORDER BY level " + dir + ", id ASC";
            case CREATED -> " ORDER BY created_at " + dir + ", id ASC";
            case DEFAULT -> DEFAULT_ORDER_BY;
        };
    }

    /** 뷰의 {@code tag_names}(쉼표 구분, 태그 id 순)를 목록으로 바꾼다. 태그가 없으면(NULL) 빈 목록. */
    private static List<String> splitTags(String tagNames) {
        if (tagNames == null) {
            return List.of();
        }
        return List.of(tagNames.split(","));
    }
}
