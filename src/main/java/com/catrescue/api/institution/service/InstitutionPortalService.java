package com.catrescue.api.institution.service;

import com.catrescue.api.institution.dto.CatPriorityDto;
import com.catrescue.api.institution.dto.InstitutionPortalProfileDto;
import com.catrescue.api.institution.dto.PartnerVolunteerDto;
import com.catrescue.api.institution.dto.PatchFeedingStationRequest;
import com.catrescue.api.institution.persistence.OrganizationEntity;
import com.catrescue.api.institution.persistence.InstitutionVolunteerEntity;
import com.catrescue.api.institution.repository.InstitutionVolunteerJpaRepository;
import com.catrescue.api.tracking.domain.SterilizationStatus;
import com.catrescue.api.tracking.persistence.CatEntity;
import com.catrescue.api.tracking.persistence.FeedingStationEntity;
import com.catrescue.api.tracking.repository.CatJpaRepository;
import com.catrescue.api.tracking.repository.FeedingStationJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class InstitutionPortalService {

    private final CatJpaRepository catJpaRepository;
    private final FeedingStationJpaRepository feedingStationJpaRepository;
    private final InstitutionVolunteerJpaRepository institutionVolunteerJpaRepository;

    public InstitutionPortalService(
            CatJpaRepository catJpaRepository,
            FeedingStationJpaRepository feedingStationJpaRepository,
            InstitutionVolunteerJpaRepository institutionVolunteerJpaRepository
    ) {
        this.catJpaRepository = catJpaRepository;
        this.feedingStationJpaRepository = feedingStationJpaRepository;
        this.institutionVolunteerJpaRepository = institutionVolunteerJpaRepository;
    }

    public InstitutionPortalProfileDto profile(OrganizationEntity org) {
        return new InstitutionPortalProfileDto(
                org.getId(),
                org.getName(),
                org.getOrgType(),
                org.getSubscriptionTier(),
                org.getSubscriptionExpiresAt(),
                org.getTerritoryMinLat(),
                org.getTerritoryMaxLat(),
                org.getTerritoryMinLng(),
                org.getTerritoryMaxLng()
        );
    }

    public List<CatPriorityDto> priorityCats(OrganizationEntity org) {
        List<CatEntity> cats = catJpaRepository.findLastSeenInTerritory(
                org.getTerritoryMinLat(),
                org.getTerritoryMaxLat(),
                org.getTerritoryMinLng(),
                org.getTerritoryMaxLng()
        );
        List<CatPriorityDto> rows = new ArrayList<>();
        Instant now = Instant.now();
        for (CatEntity c : cats) {
            Priority p = scoreCat(c, now);
            rows.add(new CatPriorityDto(
                    c.getId(),
                    c.getDisplayName(),
                    c.getLastSeenLat(),
                    c.getLastSeenLng(),
                    c.getLastSeenAt(),
                    c.isEarTipped(),
                    c.getSterilizationStatus(),
                    p.score(),
                    p.reason()
            ));
        }
        rows.sort(Comparator.comparingDouble(CatPriorityDto::priorityScore).reversed());
        return rows;
    }

    private record Priority(double score, String reason) {
    }

    private static Priority scoreCat(CatEntity c, Instant now) {
        long days = ChronoUnit.DAYS.between(c.getLastSeenAt(), now);
        if (days < 0) {
            days = 0;
        }
        double daysScore = Math.min(40, days * 2.0);

        double need = 0;
        List<String> bits = new ArrayList<>();
        if (!c.isEarTipped()) {
            need += 35;
            bits.add("no ear-tip visible");
        }
        if (c.getSterilizationStatus() == SterilizationStatus.UNKNOWN) {
            need += 12;
            bits.add("sterilization unknown");
        }
        if (c.getSterilizationStatus() == SterilizationStatus.NOT_STERILIZED) {
            need += 28;
            bits.add("likely not sterilized");
        }
        double score = daysScore + need;
        String reason = bits.isEmpty()
                ? "monitor — stale sighting weight (" + days + "d)"
                : String.join("; ", bits) + "; stale weight (" + days + "d)";
        return new Priority(score, reason);
    }

    public String exportCatsHealthCsv(OrganizationEntity org) {
        List<CatPriorityDto> rows = priorityCats(org);
        StringBuilder sb = new StringBuilder();
        sb.append("cat_id,display_name,last_seen_lat,last_seen_lng,last_seen_at,ear_tipped,sterilization_status,priority_score,priority_reason\n");
        for (CatPriorityDto r : rows) {
            sb.append(r.catId()).append(',');
            sb.append(csvEscape(r.displayName())).append(',');
            sb.append(r.lastSeenLat()).append(',');
            sb.append(r.lastSeenLng()).append(',');
            sb.append(r.lastSeenAt() != null ? r.lastSeenAt().toString() : "").append(',');
            sb.append(r.earTipped()).append(',');
            sb.append(r.sterilizationStatus() != null ? r.sterilizationStatus().name() : "").append(',');
            sb.append(String.format(Locale.ROOT, "%.2f", r.priorityScore())).append(',');
            sb.append(csvEscape(r.priorityReason())).append('\n');
        }
        return sb.toString();
    }

    private static String csvEscape(String raw) {
        String s = raw == null ? "" : raw;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public List<FeedingStationEntity> listFeedingStations(OrganizationEntity org) {
        List<FeedingStationEntity> inBox = feedingStationJpaRepository.findInTerritory(
                org.getTerritoryMinLat(),
                org.getTerritoryMaxLat(),
                org.getTerritoryMinLng(),
                org.getTerritoryMaxLng()
        );
        List<FeedingStationEntity> assigned = feedingStationJpaRepository.findByManagedByOrganizationIdOrderByIdAsc(org.getId());
        List<FeedingStationEntity> merged = new ArrayList<>(inBox);
        for (FeedingStationEntity a : assigned) {
            boolean dup = merged.stream().anyMatch(x -> x.getId().equals(a.getId()));
            if (!dup) {
                merged.add(a);
            }
        }
        merged.sort(Comparator.comparingLong(FeedingStationEntity::getId));
        return merged;
    }

    public FeedingStationEntity patchFeedingStation(OrganizationEntity org, long stationId, PatchFeedingStationRequest body) {
        FeedingStationEntity row = feedingStationJpaRepository.findById(stationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "feeding station not found"));
        if (!stationInTerritory(org, row) && !org.getId().equals(row.getManagedByOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "station outside jurisdiction");
        }
        if (body.partnerNotes() != null) {
            row.setPartnerNotes(body.partnerNotes());
        }
        if (Boolean.TRUE.equals(body.assignToMyOrganization())) {
            row.setManagedByOrganizationId(org.getId());
        }
        if (Boolean.FALSE.equals(body.assignToMyOrganization())) {
            row.setManagedByOrganizationId(null);
        }
        return feedingStationJpaRepository.save(row);
    }

    private static boolean stationInTerritory(OrganizationEntity org, FeedingStationEntity row) {
        double lat = row.getLatitude();
        double lng = row.getLongitude();
        return lat >= org.getTerritoryMinLat() && lat <= org.getTerritoryMaxLat()
                && lng >= org.getTerritoryMinLng() && lng <= org.getTerritoryMaxLng();
    }

    public List<PartnerVolunteerDto> listVolunteers(OrganizationEntity org) {
        return institutionVolunteerJpaRepository.findByOrganizationIdOrderByCreatedAtDesc(org.getId()).stream()
                .map(v -> new PartnerVolunteerDto(v.getId(), v.getEmail(), v.getDisplayName(), v.getRoleTag()))
                .toList();
    }

    public PartnerVolunteerDto addVolunteer(OrganizationEntity org, String email, String displayName, String roleTag) {
        String em = email.trim().toLowerCase(Locale.ROOT);
        if (institutionVolunteerJpaRepository.existsByOrganizationIdAndEmailIgnoreCase(org.getId(), em)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "volunteer email already registered");
        }
        InstitutionVolunteerEntity v = new InstitutionVolunteerEntity();
        v.setOrganizationId(org.getId());
        v.setEmail(em);
        v.setDisplayName(displayName != null ? displayName.trim() : null);
        v.setRoleTag(roleTag != null ? roleTag.trim() : null);
        InstitutionVolunteerEntity saved = institutionVolunteerJpaRepository.save(v);
        return new PartnerVolunteerDto(saved.getId(), saved.getEmail(), saved.getDisplayName(), saved.getRoleTag());
    }

    public void deleteVolunteer(OrganizationEntity org, long volunteerId) {
        InstitutionVolunteerEntity v = institutionVolunteerJpaRepository.findById(volunteerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "volunteer not found"));
        if (!v.getOrganizationId().equals(org.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "volunteer belongs to another organization");
        }
        institutionVolunteerJpaRepository.delete(v);
    }
}
