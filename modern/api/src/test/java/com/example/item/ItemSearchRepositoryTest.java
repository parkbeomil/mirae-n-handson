package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.item.ItemSearchCondition.SortKey;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 검색 저장소 슬라이스 — H2(MariaDB 모드).
 * 테스트 프로필은 엔티티로 테이블만 만들므로, 운영 스키마(db/mariadb/init/01-schema.sql)의 v_item_public 뷰를
 * 테스트 안에서만 같은 정의로 만든다.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ItemSearchRepository.class)
@Sql(statements = ItemSearchRepositoryTest.CREATE_PUBLIC_VIEW)
class ItemSearchRepositoryTest {

    static final String CREATE_PUBLIC_VIEW = """
        CREATE OR REPLACE VIEW v_item_public AS
        SELECT i.id, i.unit_id, u.code AS unit_code, u.name AS unit_name, u.grade AS unit_grade,
               i.title, i.stem, i.level, i.created_at, i.updated_at,
               (SELECT GROUP_CONCAT(t.name ORDER BY t.id SEPARATOR ',')
                  FROM item_tag it JOIN tag t ON t.id = it.tag_id
                 WHERE it.item_id = i.id) AS tag_names
          FROM item i
          JOIN unit u ON u.id = i.unit_id
         WHERE i.status = 'A';
        """;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemSearchRepository itemSearchRepository;

    @BeforeEach
    void seed() {
        Unit fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, word, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, fraction, "삭제된 문항", 2, ItemStatus.DELETED));
        entityManager.persist(ItemFixtures.item(null, fraction, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 4, ItemStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();
    }

    private static ItemSearchCondition condition(String keyword, String unitCode, Long level, String tag) {
        return new ItemSearchCondition(keyword, unitCode, level, tag, SortKey.DEFAULT, false, 1);
    }

    private static ItemSearchCondition sorted(SortKey sortKey, boolean descending) {
        return new ItemSearchCondition(null, null, null, null, sortKey, descending, 1);
    }

    @Test
    @DisplayName("난이도 입력이 비면 공개 문항 중 난이도 5를 뺀다 · 기본 정렬은 level DESC, id ASC")
    void excludesNonPublicAndLevelFiveByDefault() {
        ItemSearchCondition condition = condition(null, null, null, null);

        assertThat(itemSearchRepository.count(condition)).isEqualTo(3);
        assertThat(itemSearchRepository.findPage(condition)).extracting(ItemSearchRow::title)
            .containsExactly("분수 문장제", "소수 곱셈", "분모가 같은 분수의 덧셈");
    }

    @Test
    @DisplayName("난이도를 주면 그 값과 같은 문항만(5 포함), 범위 밖 값은 0건")
    void filtersByExactLevel() {
        assertThat(itemSearchRepository.findPage(condition(null, null, 5L, null)))
            .extracting(ItemSearchRow::title).containsExactly("난이도 5 분수");
        assertThat(itemSearchRepository.count(condition(null, null, Long.MAX_VALUE, null))).isZero();
        assertThat(itemSearchRepository.count(condition(null, null, 0L, null))).isZero();
    }

    @Test
    @DisplayName("키워드는 제목 또는 지문 LIKE, % 는 와일드카드로 동작한다")
    void matchesKeywordInTitleOrStemAsWildcard() {
        assertThat(itemSearchRepository.findPage(condition("분수", null, null, null)))
            .extracting(ItemSearchRow::title).containsExactly("분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(itemSearchRepository.count(condition("문제 본문", null, null, null))).isEqualTo(3);
        assertThat(itemSearchRepository.count(condition("%", null, null, null))).isEqualTo(3);
    }

    @Test
    @DisplayName("단원 · 태그는 정확히 일치, 없는 단원은 오류 없이 0건")
    void filtersByUnitAndTag() {
        assertThat(itemSearchRepository.findPage(condition(null, "M5-3", null, null)))
            .extracting(ItemSearchRow::title).containsExactly("소수 곱셈");
        assertThat(itemSearchRepository.findPage(condition(null, null, null, "문장제")))
            .extracting(ItemSearchRow::title).containsExactly("분수 문장제");
        assertThat(itemSearchRepository.count(condition(null, "Z9-99", null, null))).isZero();
        assertThat(itemSearchRepository.count(condition(null, null, null, "문장"))).isZero();
    }

    @Test
    @DisplayName("행은 id · title · unit(코드) · level · tags(태그 id 순, 없으면 빈 목록)")
    void mapsRowWithTagsInIdOrder() {
        List<ItemSearchRow> rows = itemSearchRepository.findPage(condition(null, null, 4L, null));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).unit()).isEqualTo("M5-1");
        assertThat(rows.get(0).level()).isEqualTo(4);
        assertThat(rows.get(0).tags()).containsExactly("계산", "문장제");
        assertThat(rows.get(1).tags()).isEmpty();
    }

    @Test
    @DisplayName("정렬: title 내림차순 · unit 오름차순(같은 단원은 level DESC)")
    void ordersByRequestedKey() {
        assertThat(itemSearchRepository.findPage(sorted(SortKey.TITLE, true))).extracting(ItemSearchRow::title)
            .containsExactly("소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(itemSearchRepository.findPage(sorted(SortKey.UNIT, false))).extracting(ItemSearchRow::title)
            .containsExactly("분수 문장제", "분모가 같은 분수의 덧셈", "소수 곱셈");
        assertThat(itemSearchRepository.findPage(sorted(SortKey.LEVEL, false))).extracting(ItemSearchRow::level)
            .containsExactly(2, 4, 4);
    }

    @Test
    @DisplayName("전체 건수보다 뒤 페이지는 빈 목록이고 건수는 그대로다")
    void returnsEmptyPageBeyondTotal() {
        ItemSearchCondition page2 = new ItemSearchCondition(null, null, null, null, SortKey.DEFAULT, false, 2);

        assertThat(itemSearchRepository.findPage(page2)).isEmpty();
        assertThat(itemSearchRepository.count(page2)).isEqualTo(3);
    }
}
