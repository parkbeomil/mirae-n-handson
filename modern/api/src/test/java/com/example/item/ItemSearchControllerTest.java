package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — ItemController 를 함께 올려 /api/items/{id} 와 경로가 겹치지 않는지도 본다. */
@WebMvcTest({ItemSearchController.class, ItemController.class})
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @MockBean
    private ItemService itemService;

    @Test
    @DisplayName("GET /api/items/search → 200, {rows, count, message} 이고 행에는 정규화 필드만 있다")
    void searchReturnsNormalizedShape() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(
            List.of(new ItemSearchRow(12, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음"))), 1, null));

        mockMvc.perform(get("/api/items/search").param("unit", "M5-1").param("level", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.message").value(nullValue()))
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].id").value(12))
            .andExpect(jsonPath("$.rows[0].title").value("대분수의 덧셈"))
            .andExpect(jsonPath("$.rows[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.rows[0].level").value(3))
            .andExpect(jsonPath("$.rows[0].tags[1]").value("오답률높음"))
            .andExpect(jsonPath("$.rows[0].stem").doesNotExist())
            .andExpect(jsonPath("$.rows[0].status").doesNotExist())
            .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    @DisplayName("0건이어도 404 가 아니라 200, 안내 문구와 빈 rows")
    void searchWithNoResultReturns200() throws Exception {
        when(itemSearchService.search(any())).thenReturn(
            new ItemSearchResponse(List.of(), 0, "검색 결과가 없습니다"));

        mockMvc.perform(get("/api/items/search").param("unit", "Z9-99").param("level", "abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.message").value("검색 결과가 없습니다"))
            .andExpect(jsonPath("$.rows").isEmpty());
    }

    @Test
    @DisplayName("파라미터는 이름 · 값 그대로(level[] 배열 포함) 서비스에 넘긴다")
    @SuppressWarnings("unchecked")
    void passesRawParamsToService() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(List.of(), 0, "검색 결과가 없습니다"));

        mockMvc.perform(get("/api/items/search").param("level[]", "5", "1").param("page", "-1"))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(itemSearchService).search(captor.capture());
        assertThat(captor.getValue().get("level[]")).containsExactly("5", "1");
        assertThat(captor.getValue().get("page")).containsExactly("-1");
    }
}
