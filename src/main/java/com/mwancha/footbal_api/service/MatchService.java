package com.mwancha.footbal_api.service;

import com.mwancha.footbal_api.dto.MatchDto;
import com.mwancha.footbal_api.repository.MatchRepositoryJdbc;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service
public class MatchService {
    private final MatchRepositoryJdbc repo;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public MatchService(MatchRepositoryJdbc repo) {
        this.repo = repo;
    }

    public List<MatchDto> getMatches(String date, String league, int page, int size) {
        String dateParam = null;
        if (StringUtils.hasText(date)) {
            // Accept either a plain date yyyy-MM-dd or an ISO date/time (parse and extract date)
            try {
                DATE_FMT.parse(date);
                dateParam = date;
            } catch (DateTimeParseException ex1) {
                try {
                    // Try offset datetime like 2025-11-12T15:00:00Z or with offset
                    OffsetDateTime odt = OffsetDateTime.parse(date);
                    dateParam = odt.toLocalDate().toString();
                } catch (DateTimeParseException ex2) {
                    try {
                        // Try local datetime without offset
                        LocalDateTime ldt = LocalDateTime.parse(date);
                        dateParam = ldt.toLocalDate().toString();
                    } catch (DateTimeParseException ex3) {
                        // Return a 400 Bad Request with a helpful message instead of a 500
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use yyyy-MM-dd or ISO datetime");
                    }
                }
            }
        }

        int safeSize = Math.min(Math.max(size, 1), 1000);
        int safePage = Math.max(page, 0);
        int limit = safeSize;
        int offset = safePage * limit;

        return repo.findByDateAndLeague(dateParam, league, limit, offset);
    }

    public List<String> getLeagues() {
        return repo.findLeagues();
    }

    public List<MatchDto> getAllMatches(String date, String league) {
        String dateParam = null;
        if (StringUtils.hasText(date)) {
            try {
                DATE_FMT.parse(date);
                dateParam = date;
            } catch (DateTimeParseException ex1) {
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(date);
                    dateParam = odt.toLocalDate().toString();
                } catch (DateTimeParseException ex2) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(date);
                        dateParam = ldt.toLocalDate().toString();
                    } catch (DateTimeParseException ex3) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Use yyyy-MM-dd or ISO datetime");
                    }
                }
            }
        }

        return repo.findAllByDateAndLeague(dateParam, league);
    }

    public boolean setMatchTime(Long id, String matchTime) {
        int updated = repo.updateMatchTime(id, matchTime);
        return updated > 0;
    }

    /**
     * Refresh match_time for all rows using the provided target timezone.
     * If force==false, only updates rows where match_time IS NULL or empty.
     * Returns number of rows updated.
     */
    public int refreshMatchTimes(java.time.ZoneId targetZone, boolean force) {
        var rows = repo.findAllKickoffs();
        int updated = 0;
        for (var row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            Object kickoffObj = row.get("kickoff");
            if (kickoffObj == null) continue;

            java.time.Instant instant = null;
            try {
                if (kickoffObj instanceof java.sql.Timestamp) {
                    instant = ((java.sql.Timestamp) kickoffObj).toInstant();
                } else if (kickoffObj instanceof java.sql.Date) {
                    instant = ((java.sql.Date) kickoffObj).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                } else {
                    String s = kickoffObj.toString();
                    try { instant = java.time.OffsetDateTime.parse(s).toInstant(); }
                    catch (java.time.format.DateTimeParseException ex1) {
                        try { instant = java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toInstant(); }
                        catch (java.time.format.DateTimeParseException ex2) { instant = null; }
                    }
                }
            } catch (Exception e) {
                instant = null;
            }

            if (instant == null) continue;

            // Format to HH:mm in target zone
            String timeStr = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(targetZone).format(instant);

            if (!force) {
                // check existing match_time value to avoid overwriting
                try {
                    String existing = repo.findMatchTimeById(id);
                    if (existing != null && !existing.isBlank()) continue; // skip
                } catch (Exception ignored) {}
            }

            int r = repo.updateMatchTime(id, timeStr);
            if (r > 0) updated += r;
        }
        return updated;
    }
}