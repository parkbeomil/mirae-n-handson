package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.item.ItemSearchCondition.SortKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemSearchRepository itemSearchRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    private ItemSearchCondition searchWith(Map<String, List<String>> params) {
        when(itemSearchRepository.count(any())).thenReturn(1L);
        when(itemSearchRepository.findPage(any())).thenReturn(List.of());
        itemSearchService.search(params);
        ArgumentCaptor<ItemSearchCondition> captor = ArgumentCaptor.forClass(ItemSearchCondition.class);
        verify(itemSearchRepository).count(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("파라미터가 없으면 조건 없음 · 난이도 기본 필터(null) · 기본 정렬 · 1페이지")
    void usesDefaultsWithoutParams() {
        ItemSearchCondition condition = searchWith(Map.of());

        assertThat(condition.keyword()).isNull();
        assertThat(condition.unitCode()).isNull();
        assertThat(condition.level()).isNull();
        assertThat(condition.tag()).isNull();
        assertThat(condition.sortKey()).isEqualTo(SortKey.DEFAULT);
        assertThat(condition.page()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 건수가 0이면 안내 문구, 아니면 null · 건수는 행 수가 아니라 전체 건수")
    void setsMessageOnlyWhenTotalIsZero() {
        when(itemSearchRepository.count(any())).thenReturn(0L, 20L);
        when(itemSearchRepository.findPage(any())).thenReturn(List.of(), List.of());

        ItemSearchResponse empty = itemSearchService.search(Map.of("unit", List.of("Z9-99")));
        ItemSearchResponse emptyPage = itemSearchService.search(Map.of("page", List.of("2")));

        assertThat(empty.count()).isZero();
        assertThat(empty.message()).isEqualTo("검색 결과가 없습니다");
        assertThat(empty.rows()).isEmpty();
        assertThat(emptyPage.count()).isEqualTo(20);
        assertThat(emptyPage.message()).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백은 PHP trim 대상 문자만 지우고, 공백뿐이면 조건에서 뺀다")
    void trimsLikePhp() {
        ItemSearchCondition condition = searchWith(Map.of(
            "q", List.of("   "), "unit", List.of(" M5-1\t"), "tag", List.of("\f계산")));

        assertThat(condition.keyword()).isNull();
        assertThat(condition.unitCode()).isEqualTo("M5-1");
        assertThat(condition.tag()).isEqualTo("\f계산");
    }

    @Test
    @DisplayName("키워드는 코드포인트 100자, 태그는 50자까지만 쓴다")
    void cutsKeywordAndTagByCodePoints() {
        ItemSearchCondition condition = searchWith(Map.of(
            "q", List.of("😀".repeat(101)), "tag", List.of("가".repeat(51))));

        assertThat(condition.keyword().codePointCount(0, condition.keyword().length())).isEqualTo(100);
        assertThat(condition.tag()).isEqualTo("가".repeat(50));
    }

    @Test
    @DisplayName("키워드 · 단원은 입력값 그대로 넘긴다(와일드카드 · 소문자 그대로)")
    void keepsWildcardKeywordAndLowercaseUnit() {
        ItemSearchCondition condition = searchWith(Map.of("q", List.of("%"), "unit", List.of("m5-1")));

        assertThat(condition.keyword()).isEqualTo("%");
        assertThat(condition.unitCode()).isEqualTo("m5-1");
    }

    @ParameterizedTest(name = "level={0} → {1}")
    @CsvSource({
        "3, 3",
        "05, 5",
        "abc, 0",
        "-1, -1",
        "3.9, 3",
        "1e1, 10",
        "3abc, 3",
        "+3, 3",
        "0x1A, 0",
        "99999999999999999999, 9223372036854775807",
        "1e100, 9223372036854775807",
    })
    @DisplayName("난이도는 1~5 한 자리면 그 값, 그 밖에는 PHP (int) 변환값으로 비교한다")
    void convertsLevelLikePhpIntCast(String raw, long expected) {
        ItemSearchCondition condition = searchWith(Map.of("level", List.of(raw)));

        assertThat(condition.level()).isEqualTo(expected);
    }

    @Test
    @DisplayName("난이도가 빈 값이면 null(기본 필터)")
    void leavesEmptyLevelAsNull() {
        ItemSearchCondition condition = searchWith(Map.of("level", List.of(" ")));

        assertThat(condition.level()).isNull();
    }

    @Test
    @DisplayName("배열 파라미터(level[])는 첫 값을 쓴다")
    void usesFirstValueOfArrayParam() {
        ItemSearchCondition condition = searchWith(Map.of("level[]", List.of("5", "1")));

        assertThat(condition.level()).isEqualTo(5L);
    }

    @Test
    @DisplayName("같은 이름이 반복되면 마지막 값을 쓴다")
    void usesLastValueOfRepeatedParam() {
        ItemSearchCondition condition = searchWith(Map.of("level", List.of("3", "4")));

        assertThat(condition.level()).isEqualTo(4L);
    }

    @ParameterizedTest(name = "sort={0}, dir={1} → {2} desc={3}")
    @CsvSource({
        "'', '', DEFAULT, false",
        "DROP, up, DEFAULT, false",
        "title, asc, TITLE, false",
        "TITLE, '', TITLE, false",
        "id, '', ID, false",
        "unit, DESC, UNIT, true",
        "level, '', LEVEL, true",
        "level, asc, LEVEL, false",
        "created, '', CREATED, true",
        "id, up, ID, false",
    })
    @DisplayName("정렬 기준 · 방향: 모르는 기준은 기본 정렬, 방향이 없으면 level · created 만 내림차순")
    void resolvesSort(String sort, String dir, SortKey expectedKey, boolean expectedDescending) {
        ItemSearchCondition condition = searchWith(Map.of("sort", List.of(sort), "dir", List.of(dir)));

        assertThat(condition.sortKey()).isEqualTo(expectedKey);
        assertThat(condition.descending()).isEqualTo(expectedDescending);
    }

    @ParameterizedTest(name = "page={0} → {1}")
    @CsvSource({
        "'', 1",
        "0, 1",
        "2, 2",
        "-1, 1",
        "abc, 1",
        "999, 999",
        "1000, 999",
        "99999999999999999999, 999",
        "' 2', 1",
    })
    @DisplayName("페이지: 숫자만 인정, 0은 1, 999 초과는 999, 그 밖의 형식은 1")
    void clampsPage(String raw, int expected) {
        ItemSearchCondition condition = searchWith(Map.of("page", List.of(raw)));

        assertThat(condition.page()).isEqualTo(expected);
    }
}
