package com.example.item;

import java.util.List;

/**
 * {@code GET /api/items/search} 응답.
 *
 * @param rows    현재 페이지의 행
 * @param count   조건에 맞는 전체 건수(현재 페이지 행 수가 아니다)
 * @param message 전체 건수가 0이면 안내 문구, 아니면 {@code null}
 */
public record ItemSearchResponse(List<ItemSearchRow> rows, long count, String message) {
}
