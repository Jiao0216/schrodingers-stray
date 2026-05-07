package com.catrescue.api.tracking.service;

import com.catrescue.api.tracking.dto.CreateFeedingRecordRequest;
import com.catrescue.api.tracking.dto.FeedingRecordResponse;
import com.catrescue.api.tracking.dto.OverdueCatResponse;
import com.catrescue.api.tracking.persistence.FeedingRecordEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.FeedingRecordJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FeedingService {

    private final CatJpaRepository catJpaRepository;
    private final FeedingRecordJpaRepository feedingRecordJpaRepository;

    public FeedingService(CatJpaRepository catJpaRepository, FeedingRecordJpaRepository feedingRecordJpaRepository) {
        this.catJpaRepository = catJpaRepository;
        this.feedingRecordJpaRepository = feedingRecordJpaRepository;
    }

    @Transactional
    public Optional<FeedingRecordResponse> recordFeeding(long catId, CreateFeedingRecordRequest body) {
        if (catJpaRepository.findById(catId).isEmpty()) {
            return Optional.empty();
        }
        Instant fedAt = body != null && body.fedAt() != null ? body.fedAt() : Instant.now();
        String notes = body != null ? body.notes() : null;
        Long reporter = body != null ? body.reporterUserId() : null;

        FeedingRecordEntity row = new FeedingRecordEntity();
        row.setCatId(catId);
        row.setFedAt(fedAt);
        row.setReporterUserId(reporter);
        row.setNotes(notes);
        row.setCreatedAt(Instant.now());
        feedingRecordJpaRepository.save(row);
        return Optional.of(toDto(row));
    }

    public List<FeedingRecordResponse> listForCat(long catId) {
        return feedingRecordJpaRepository.findByCatIdOrderByFedAtDesc(catId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<OverdueCatResponse> listCatsWithoutRecentFeeding(int hours) {
        int h = Math.max(1, Math.min(hours, 24 * 365));
        Instant since = Instant.now().minus(h, ChronoUnit.HOURS);
        List<Long> ids = feedingRecordJpaRepository.findCatIdsWithoutFeedingSince(since);
        List<OverdueCatResponse> out = new ArrayList<>();
        for (Long id : ids) {
            catJpaRepository.findById(id).ifPresent(cat -> {
                Instant lastFed = feedingRecordJpaRepository.findFirstByCatIdOrderByFedAtDesc(id)
                        .map(FeedingRecordEntity::getFedAt)
                        .orElse(null);
                String name = cat.getDisplayName() != null ? cat.getDisplayName() : "";
                out.add(new OverdueCatResponse(cat.getId(), name, lastFed));
            });
        }
        return out;
    }

    private FeedingRecordResponse toDto(FeedingRecordEntity e) {
        return new FeedingRecordResponse(
                e.getId(),
                e.getCatId(),
                e.getFedAt(),
                e.getReporterUserId(),
                e.getNotes(),
                e.getCreatedAt()
        );
    }
}
