package com.pg.entity;

import com.pg.entity.enums.Party;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// A photo. The file lives on disk (never in a public folder); the database keeps its fingerprint.
@Entity
@Table(name = "inspection_photos", indexes = @Index(name = "idx_photo_inspection", columnList = "inspection_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InspectionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    @ToString.Exclude
    private Inspection inspection;

    // optional: the report line this photo belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    @ToString.Exclude
    private InspectionItem item;

    @Column(name = "stored_name", nullable = false, length = 80)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    // SHA-256 of the file: shows later that the photo was not swapped or edited
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(length = 120)
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Party party;

    // set by the SERVER, not by the uploader, so it can not be backdated
    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}
