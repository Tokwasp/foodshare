package com.foodservice.common.response;

import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 페이징 응답 공통 DTO.
 * <p>
 * {@code of(Slice)} 를 기반으로 설계되어 있어 Page → Slice 전환 시 호출부 변경 없이
 * totalElements / totalPages 를 -1(미제공) 처리하는 것만으로 대응 가능합니다.
 * <pre>
 *   // Page 사용 (현재)
 *   PageResponse.of(page);
 *
 *   // Slice 전환 시 (향후)
 *   PageResponse.of(slice);   // totalElements = -1, totalPages = -1
 * </pre>
 */
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    private PageResponse(Slice<T> slice) {
        this.content = slice.getContent();
        this.page = slice.getNumber();
        this.size = slice.getSize();
        this.hasNext = slice.hasNext();
        this.hasPrevious = slice.hasPrevious();

        if (slice instanceof Page<T> p) {
            this.totalElements = p.getTotalElements();
            this.totalPages = p.getTotalPages();
        } else {
            // Slice 전환 시: 전체 건수 미제공
            this.totalElements = -1;
            this.totalPages = -1;
        }
    }

    /**
     * Page 또는 Slice 로부터 PageResponse 를 생성합니다.
     * Page 를 넘기면 totalElements / totalPages 가 채워지고,
     * Slice 를 넘기면 -1 로 표시됩니다.
     */
    public static <T> PageResponse<T> of(Slice<T> slice) {
        return new PageResponse<>(slice);
    }
}
