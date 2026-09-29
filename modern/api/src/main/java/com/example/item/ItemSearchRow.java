package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과 한 행. {@code unit} 은 단원 코드, {@code tags} 는 태그 이름 목록(태그 id 순, 없으면 빈 배열).
 */
public record ItemSearchRow(
    Integer id,
    String title,
    String unit,
    Integer level,
    List<String> tags) {
}
