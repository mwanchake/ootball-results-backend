package com.mwancha.footbal_api.controller;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DatabaseHealthController {
    private final NamedParameterJdbcTemplate jdbc;

    public DatabaseHealthController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dbtest")
    public Map<String, Object> dbtest() {
        Map<String, Object> res = new HashMap<>();
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Map.of(), Integer.class);
            res.put("ping", one);
        } catch (Exception e) {
            res.put("pingError", e.toString());
            return res;
        }

        try {
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM matches", Map.of(), Integer.class);
            res.put("matchesCount", total);
        } catch (Exception e) {
            res.put("countError", e.toString());
        }

        try {
            List<Map<String, Object>> sample = jdbc.getJdbcTemplate().queryForList(
                "SELECT id, hometeam, awayteam, kickoff FROM matches ORDER BY id DESC LIMIT 5"
            );
            res.put("sample", sample);
        } catch (Exception e) {
            res.put("sampleError", e.toString());
        }

        return res;
    }
}
