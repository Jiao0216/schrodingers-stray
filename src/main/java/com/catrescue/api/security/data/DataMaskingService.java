package com.catrescue.api.security.data;

import com.catrescue.api.dto.AssessmentListItemDto;
import com.catrescue.api.tracking.dto.CatLastSeenResponse;
import com.catrescue.api.tracking.dto.HeatmapSampleResponse;
import com.catrescue.api.tracking.dto.HeatmapSightingResponse;
import com.catrescue.api.tracking.dto.NearbyHelpCatDto;
import com.catrescue.api.util.CoordinatePrecision;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redacts API payloads for {@link DataAccessTier#PUBLIC} clients.
 */
@Service
public class DataMaskingService {

    public static final int PUBLIC_GEO_DECIMALS = 2;

    public List<HeatmapSampleResponse> maskHeatmapPoints(List<HeatmapSampleResponse> in, DataAccessTier tier) {
        if (tier != DataAccessTier.PUBLIC) {
            return in;
        }
        return in.stream()
                .map(p -> new HeatmapSampleResponse(
                        CoordinatePrecision.roundDegrees(p.lat(), PUBLIC_GEO_DECIMALS),
                        CoordinatePrecision.roundDegrees(p.lng(), PUBLIC_GEO_DECIMALS),
                        p.weight()
                ))
                .toList();
    }

    public List<HeatmapSightingResponse> maskHeatmapSightings(List<HeatmapSightingResponse> in, DataAccessTier tier) {
        if (tier != DataAccessTier.PUBLIC) {
            return in;
        }
        return in.stream().map(this::maskSighting).toList();
    }

    private HeatmapSightingResponse maskSighting(HeatmapSightingResponse s) {
        return new HeatmapSightingResponse(
                s.sightingId(),
                s.catId(),
                CoordinatePrecision.roundDegrees(s.lat(), PUBLIC_GEO_DECIMALS),
                CoordinatePrecision.roundDegrees(s.lng(), PUBLIC_GEO_DECIMALS),
                s.weight(),
                s.occurredAt(),
                null,
                "",
                "",
                "",
                false,
                null,
                null,
                false,
                s.feedingOverdue()
        );
    }

    public List<NearbyHelpCatDto> maskNearbyCats(List<NearbyHelpCatDto> in, DataAccessTier tier) {
        if (tier != DataAccessTier.PUBLIC) {
            return in;
        }
        return in.stream()
                .map(d -> new NearbyHelpCatDto(
                        d.catId(),
                        d.displayName(),
                        null,
                        null,
                        d.distanceMeters(),
                        d.priorityScore(),
                        d.sterilizationStatus(),
                        d.earTipped()
                ))
                .toList();
    }

    public List<AssessmentListItemDto> maskAssessmentList(List<AssessmentListItemDto> in, DataAccessTier tier) {
        if (tier != DataAccessTier.PUBLIC) {
            return in;
        }
        return in.stream().map(this::maskAssessmentRow).toList();
    }

    private AssessmentListItemDto maskAssessmentRow(AssessmentListItemDto x) {
        Double la = x.latitude() != null
                ? CoordinatePrecision.roundDegrees(x.latitude(), PUBLIC_GEO_DECIMALS)
                : null;
        Double lo = x.longitude() != null
                ? CoordinatePrecision.roundDegrees(x.longitude(), PUBLIC_GEO_DECIMALS)
                : null;
        return new AssessmentListItemDto(
                x.id(),
                x.imageUrl(),
                la,
                lo,
                truncateAddress(x.addressText()),
                x.healthStatus(),
                x.neuteredStatus(),
                x.createdAt(),
                null,
                x.imagePersisted(),
                null,
                0,
                null,
                false,
                x.needsHelpCategory(),
                x.healthyCategory(),
                x.tnrVerifiedCategory()
        );
    }

    public CatLastSeenResponse maskLastSeen(CatLastSeenResponse r, DataAccessTier tier) {
        if (tier != DataAccessTier.PUBLIC) {
            return r;
        }
        return new CatLastSeenResponse(
                r.catId(),
                r.lastSeenAt(),
                CoordinatePrecision.roundDegrees(r.lastSeenLatitude(), PUBLIC_GEO_DECIMALS),
                CoordinatePrecision.roundDegrees(r.lastSeenLongitude(), PUBLIC_GEO_DECIMALS),
                null,
                null,
                truncateAddress(r.lastAddressText())
        );
    }

    private static String truncateAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (t.length() <= 40) {
            return t;
        }
        return t.substring(0, 37) + "…";
    }
}
