package com.mwancha.footbal_api.repository;

import com.mwancha.footbal_api.dto.MatchDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MatchRepositoryJdbc {
    private final NamedParameterJdbcTemplate jdbc;

    // Configurable list of top leagues. If the property is not set, fall back to defaults.
    private final java.util.List<String> topLeagues;

    public MatchRepositoryJdbc(NamedParameterJdbcTemplate jdbc, org.springframework.core.env.Environment env) {
        this.jdbc = jdbc;
        // Read comma-separated list from application properties: app.topLeagues
        String prop = env.getProperty("app.topLeagues", "").trim();
        if (prop.isEmpty()) {
            this.topLeagues = java.util.List.of("premier league", "la liga", "serie a", "bundesliga", "ligue 1");
        } else {
            this.topLeagues = java.util.Arrays.stream(prop.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .toList();
        }
    }

    public List<MatchDto> findByDateAndLeague(String date, String league, int limit, int offset) {
        StringBuilder sql = new StringBuilder();
    sql.append("SELECT id, league, hometeam AS home, awayteam AS away, hometeam_logo AS home_logo, awayteam_logo AS away_logo, hometeam_goals AS home_goals, awayteam_goals AS away_goals, kickoff AS match_date, match_time, match_completion ");
        sql.append("FROM matches ");
        sql.append("WHERE (:league IS NULL OR TRIM(LOWER(league)) = TRIM(LOWER(:league))) ");
        sql.append("AND (:date IS NULL OR DATE(kickoff) = :date) ");

        // Build a CASE expression to rank top leagues first (0..N-1) and others last (9999)
        StringBuilder caseExpr = new StringBuilder();
        caseExpr.append("CASE ");
        for (int i = 0; i < topLeagues.size(); i++) {
            // Use LIKE so we match variations (e.g. "Premier League - 2023") - treat each entry as a keyword
            String token = topLeagues.get(i).replace("'","''");
            caseExpr.append(String.format("WHEN TRIM(LOWER(league)) LIKE '%%" + token + "%%' THEN %d ", i));
        }
        caseExpr.append("ELSE 9999 END");

        // Append ordering by league_rank then kickoff, and pagination
        sql.append("ORDER BY (").append(caseExpr).append(") , kickoff ");
        sql.append("LIMIT :limit OFFSET :offset");

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("league", (league == null || league.isBlank()) ? null : league)
            .addValue("date", (date == null || date.isBlank()) ? null : date)
            .addValue("limit", limit)
            .addValue("offset", offset);

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String matchDateStr = null;
            java.time.Instant kickoffInstant = null;
            try {
                Object obj = rs.getObject("match_date");
                if (obj == null) {
                    matchDateStr = null;
                } else if (obj instanceof java.sql.Timestamp) {
                    java.sql.Timestamp ts = (java.sql.Timestamp) obj;
                    matchDateStr = ts.toInstant().toString();
                    kickoffInstant = ts.toInstant();
                } else if (obj instanceof java.sql.Date) {
                    java.sql.Date d = (java.sql.Date) obj;
                    matchDateStr = d.toLocalDate().toString();
                    kickoffInstant = d.toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                } else {
                    // Fallback: try to read as string and parse common formats
                    String s = rs.getString("match_date");
                    if (s != null && !s.isBlank()) {
                        try {
                            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(s);
                            matchDateStr = odt.toInstant().toString();
                            kickoffInstant = odt.toInstant();
                        } catch (java.time.format.DateTimeParseException ex1) {
                            try {
                                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(s);
                                matchDateStr = ldt.toString();
                                kickoffInstant = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
                            } catch (java.time.format.DateTimeParseException ex2) {
                                try {
                                    java.time.LocalDate ld = java.time.LocalDate.parse(s);
                                    matchDateStr = ld.toString();
                                    kickoffInstant = ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                                } catch (java.time.format.DateTimeParseException ex3) {
                                    // give up and keep raw string
                                    matchDateStr = s;
                                    kickoffInstant = null;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Don't let parsing errors crash the request; log and return null for the date
                org.slf4j.LoggerFactory.getLogger(MatchRepositoryJdbc.class).warn("Failed to read/parse kickoff for match id {}: {}", id, e.toString());
                matchDateStr = null;
                kickoffInstant = null;
            }

            // Defensive parsing for goals: columns may contain strings like '-' or empty values
            Integer homeGoalsVal = null;
            try {
                Object hg = rs.getObject("home_goals");
                if (hg instanceof Number) {
                    homeGoalsVal = ((Number) hg).intValue();
                } else if (hg instanceof String) {
                    String s = ((String) hg).trim();
                    if (!s.isEmpty() && !s.equals("-")) {
                        try { homeGoalsVal = Integer.parseInt(s); } catch (NumberFormatException ignored) { homeGoalsVal = null; }
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MatchRepositoryJdbc.class).warn("Failed to parse home_goals for id {}: {}", id, e.toString());
                homeGoalsVal = null;
            }

            Integer awayGoalsVal = null;
            try {
                Object ag = rs.getObject("away_goals");
                if (ag instanceof Number) {
                    awayGoalsVal = ((Number) ag).intValue();
                } else if (ag instanceof String) {
                    String s = ((String) ag).trim();
                    if (!s.isEmpty() && !s.equals("-")) {
                        try { awayGoalsVal = Integer.parseInt(s); } catch (NumberFormatException ignored) { awayGoalsVal = null; }
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MatchRepositoryJdbc.class).warn("Failed to parse away_goals for id {}: {}", id, e.toString());
                awayGoalsVal = null;
            }

            String matchTimeStr = null;
            try { matchTimeStr = rs.getString("match_time"); } catch (Exception ignore) { matchTimeStr = null; }

            String matchCompletion = null;
            try { matchCompletion = rs.getString("match_completion"); } catch (Exception ignore) { matchCompletion = null; }

            // Determine whether the match has been played.
            // Priority: match_completion (explicit flag) -> kickoff datetime -> goals presence as a fallback.
            boolean played = false;
            if (matchCompletion != null) {
                String mc = matchCompletion.trim().toLowerCase();
                if (mc.contains("ft") || mc.contains("finished") || mc.contains("full") || mc.contains("ended") || mc.contains("final")) {
                    played = true;
                }
            }

            if (!played) {
                if (kickoffInstant != null) {
                    try {
                        if (kickoffInstant.isBefore(java.time.Instant.now())) {
                            played = true;
                        } else {
                            played = false; // future kickoff -> not played, even if goals are present (avoid 0-0)
                        }
                    } catch (Exception ignore) {
                        // fallback to goals-based decision below
                    }
                }
            }

            if (!played) {
                // Fallback: if we have goal values but no kickoff/completion info, mark as played
                // only when at least one of the goals is non-zero. This avoids treating stored 0-0
                // (commonly used as a placeholder) as a played match.
                if ((homeGoalsVal != null || awayGoalsVal != null)) {
                    boolean anyGoalPositive = false;
                    if (homeGoalsVal != null && homeGoalsVal > 0) anyGoalPositive = true;
                    if (awayGoalsVal != null && awayGoalsVal > 0) anyGoalPositive = true;
                    if (anyGoalPositive) played = true;
                }
            }

            // If it wasn't played, hide scores by making them null so frontend won't show 0-0 for scheduled matches
            if (!played) {
                homeGoalsVal = null;
                awayGoalsVal = null;
            }

            return new MatchDto(
                id,
                rs.getString("league"),
                rs.getString("home"),
                rs.getString("away"),
                // logos
                rs.getString("home_logo"),
                rs.getString("away_logo"),
                homeGoalsVal,
                awayGoalsVal,
                matchDateStr,
                matchTimeStr
            );
        });
    }

    // Return a list of distinct league names (simple helper for frontend dropdown)
    public List<String> findLeagues() {
        // Build CASE ordering to put top leagues first
        StringBuilder caseExpr = new StringBuilder();
        caseExpr.append("CASE ");
        for (int i = 0; i < topLeagues.size(); i++) {
            String token = topLeagues.get(i).replace("'","''");
            caseExpr.append(String.format("WHEN TRIM(LOWER(league)) LIKE '%%" + token + "%%' THEN %d ", i));
        }
        caseExpr.append("ELSE 9999 END");

        String sql = "SELECT DISTINCT league FROM matches ORDER BY (" + caseExpr.toString() + "), league";
        return jdbc.query(sql, (rs, rowNum) -> rs.getString("league"));
    }

    /**
     * Find all matches for the given date and league (no LIMIT/OFFSET). Results are ordered
     * by configured top leagues first (CASE expression) and then by kickoff.
     */
    public List<MatchDto> findAllByDateAndLeague(String date, String league) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, league, hometeam AS home, awayteam AS away, hometeam_logo AS home_logo, awayteam_logo AS away_logo, hometeam_goals AS home_goals, awayteam_goals AS away_goals, kickoff AS match_date, match_time, match_completion ");
        sql.append("FROM matches ");
        sql.append("WHERE (:league IS NULL OR TRIM(LOWER(league)) = TRIM(LOWER(:league))) ");
        sql.append("AND (:date IS NULL OR DATE(kickoff) = :date) ");

        StringBuilder caseExpr = new StringBuilder();
        caseExpr.append("CASE ");
        for (int i = 0; i < topLeagues.size(); i++) {
            String token = topLeagues.get(i).replace("'","''");
            caseExpr.append(String.format("WHEN TRIM(LOWER(league)) LIKE '%%" + token + "%%' THEN %d ", i));
        }
        caseExpr.append("ELSE 9999 END");

        sql.append("ORDER BY (").append(caseExpr).append(") , kickoff ");

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("league", (league == null || league.isBlank()) ? null : league)
            .addValue("date", (date == null || date.isBlank()) ? null : date);

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            // Reuse the same row-mapping logic as in findByDateAndLeague
            Long id = rs.getLong("id");
            String matchDateStr = null;
            java.time.Instant kickoffInstant = null;
            try {
                Object obj = rs.getObject("match_date");
                if (obj == null) {
                    matchDateStr = null;
                } else if (obj instanceof java.sql.Timestamp) {
                    java.sql.Timestamp ts = (java.sql.Timestamp) obj;
                    matchDateStr = ts.toInstant().toString();
                    kickoffInstant = ts.toInstant();
                } else if (obj instanceof java.sql.Date) {
                    java.sql.Date d = (java.sql.Date) obj;
                    matchDateStr = d.toLocalDate().toString();
                    kickoffInstant = d.toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                } else {
                    String s = rs.getString("match_date");
                    if (s != null && !s.isBlank()) {
                        try {
                            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(s);
                            matchDateStr = odt.toInstant().toString();
                            kickoffInstant = odt.toInstant();
                        } catch (java.time.format.DateTimeParseException ex1) {
                            try {
                                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(s);
                                matchDateStr = ldt.toString();
                                kickoffInstant = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
                            } catch (java.time.format.DateTimeParseException ex2) {
                                try {
                                    java.time.LocalDate ld = java.time.LocalDate.parse(s);
                                    matchDateStr = ld.toString();
                                    kickoffInstant = ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                                } catch (java.time.format.DateTimeParseException ex3) {
                                    matchDateStr = s;
                                    kickoffInstant = null;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MatchRepositoryJdbc.class).warn("Failed to read/parse kickoff for match id {}: {}", id, e.toString());
                matchDateStr = null;
                kickoffInstant = null;
            }

            Integer homeGoalsVal = null;
            try {
                Object hg = rs.getObject("home_goals");
                if (hg instanceof Number) {
                    homeGoalsVal = ((Number) hg).intValue();
                } else if (hg instanceof String) {
                    String s = ((String) hg).trim();
                    if (!s.isEmpty() && !s.equals("-")) {
                        try { homeGoalsVal = Integer.parseInt(s); } catch (NumberFormatException ignored) { homeGoalsVal = null; }
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MatchRepositoryJdbc.class).warn("Failed to parse home_goals for id {}: {}", id, e.toString());
                homeGoalsVal = null;
            }

            Integer awayGoalsVal = null;
            try {
                Object ag = rs.getObject("away_goals");
                if (ag instanceof Number) {
                    awayGoalsVal = ((Number) ag).intValue();
                } else if (ag instanceof String) {
                    String s = ((String) ag).trim();
                    if (!s.isEmpty() && !s.equals("-")) {
                        try { awayGoalsVal = Integer.parseInt(s); } catch (NumberFormatException ignored) { awayGoalsVal = null; }
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MatchRepositoryJdbc.class).warn("Failed to parse away_goals for id {}: {}", id, e.toString());
                awayGoalsVal = null;
            }

            String matchTimeStr = null;
            try { matchTimeStr = rs.getString("match_time"); } catch (Exception ignore) { matchTimeStr = null; }

            String matchCompletion = null;
            try { matchCompletion = rs.getString("match_completion"); } catch (Exception ignore) { matchCompletion = null; }

            // Determine whether the match has been played.
            boolean played = false;
            if (matchCompletion != null) {
                String mc = matchCompletion.trim().toLowerCase();
                if (mc.contains("ft") || mc.contains("finished") || mc.contains("full") || mc.contains("ended") || mc.contains("final")) {
                    played = true;
                }
            }

            if (!played) {
                if (kickoffInstant != null) {
                    try {
                        if (kickoffInstant.isBefore(java.time.Instant.now())) {
                            played = true;
                        } else {
                            played = false;
                        }
                    } catch (Exception ignore) { }
                }
            }

            if (!played) {
                if ((homeGoalsVal != null || awayGoalsVal != null)) {
                    boolean anyGoalPositive = false;
                    if (homeGoalsVal != null && homeGoalsVal > 0) anyGoalPositive = true;
                    if (awayGoalsVal != null && awayGoalsVal > 0) anyGoalPositive = true;
                    if (anyGoalPositive) played = true;
                }
            }

            if (!played) {
                homeGoalsVal = null;
                awayGoalsVal = null;
            }

            return new MatchDto(
                id,
                rs.getString("league"),
                rs.getString("home"),
                rs.getString("away"),
                // logos
                rs.getString("home_logo"),
                rs.getString("away_logo"),
                homeGoalsVal,
                awayGoalsVal,
                matchDateStr,
                matchTimeStr
            );
        });
    }

    // Update the match_time column for a specific match id
    public int updateMatchTime(Long id, String matchTime) {
        String sql = "UPDATE matches SET match_time = :matchTime WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("matchTime", matchTime)
            .addValue("id", id);
        return jdbc.update(sql, params);
    }

    // Return id and kickoff for all matches where kickoff is not null
    public java.util.List<java.util.Map<String,Object>> findAllKickoffs() {
        String sql = "SELECT id, kickoff FROM matches WHERE kickoff IS NOT NULL";
        return jdbc.getJdbcTemplate().queryForList(sql);
    }

    public String findMatchTimeById(Long id) {
        try {
            String sql = "SELECT match_time FROM matches WHERE id = :id";
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
            return jdbc.queryForObject(sql, params, String.class);
        } catch (Exception e) {
            return null;
        }
    }
}