package com.example.item;

/**
 * 문항 검색 조건. {@link ItemSearchService} 가 요청 파라미터를 레거시(search.php) 규칙대로 정규화한 결과다.
 *
 * @param keyword    제목 · 지문 키워드. 없으면 {@code null}
 * @param unitCode   단원 코드(입력값 그대로). 없으면 {@code null}
 * @param level      난이도 비교값. {@code null} 이면 난이도 입력이 비어 있다는 뜻이다(기본 필터 적용)
 * @param tag        태그 이름. 없으면 {@code null}
 * @param sortKey    정렬 기준
 * @param descending 정렬 방향. {@link SortKey#DEFAULT} 에서는 쓰지 않는다
 * @param page       페이지 번호(1 ~ 999)
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    Long level,
    String tag,
    SortKey sortKey,
    boolean descending,
    int page) {

    /** 정렬 기준. {@code DEFAULT} 는 정렬 파라미터가 비었거나 알 수 없는 값일 때다. */
    public enum SortKey {
        DEFAULT, ID, TITLE, UNIT, LEVEL, CREATED
    }
}
