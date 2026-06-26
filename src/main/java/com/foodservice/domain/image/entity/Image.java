package com.foodservice.domain.image.entity;


import com.foodservice.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@NoArgsConstructor(access = PROTECTED)
//@SoftDelete
public class Image extends BaseEntity {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long imageId;

    @Column(nullable = false)
    private Long foodId;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String storedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageType imageType;

    @Builder
    private Image(Long foodId, String originalName, String storedName, ImageType imageType) {
        this.foodId = foodId;
        this.originalName = originalName;
        this.storedName = storedName;
        this.imageType = imageType;
    }
}
