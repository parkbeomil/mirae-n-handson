package com.example.item;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 검색 API. 레거시 search.php 의 이관 대상이며 파라미터 이름(q · unit · level · tag · sort · dir · page)은 같다.
 * 쿼리 문자열을 디코딩하지 않은 채 서비스에 넘긴다(파라미터 순서까지 레거시 규칙대로 해석해야 하고,
 * 형식이 틀린 값도 400 이 아니라 그 규칙대로 해석한다).
 */
@RestController
@RequestMapping("/api/items")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /** {@code GET /api/items/search} — 공개 문항 검색. */
    @GetMapping("/search")
    public ItemSearchResponse search(HttpServletRequest request) {
        return itemSearchService.search(request.getQueryString());
    }
}
