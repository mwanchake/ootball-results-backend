package com.mwancha.footbal_api.controller;

import com.mwancha.footbal_api.dto.MatchDto;
import com.mwancha.footbal_api.service.MatchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MatchController {
    private final MatchService service;

    public MatchController(MatchService service) { this.service = service; }

    @GetMapping("/matches")
    public List<MatchDto> getMatches(
            @RequestParam(required = false) String league,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return service.getMatches(date, league, page, size);
    }

    @GetMapping("/matches/all")
    public List<MatchDto> getAllMatches(
            @RequestParam(required = false) String league,
            @RequestParam(required = false) String date) {
        return service.getAllMatches(date, league);
    }

    @GetMapping("/leagues")
    public List<String> getLeagues() {
        return service.getLeagues();
    }

    @PutMapping("/matches/{id}/time")
    public Map<String, Object> updateMatchTime(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String matchTime = body == null ? null : body.get("matchTime");
        Map<String, Object> res = new java.util.HashMap<>();
        if (matchTime == null) {
            res.put("success", false);
            res.put("message", "matchTime is required in JSON body");
            return res;
        }
        boolean ok = service.setMatchTime(id, matchTime);
        res.put("success", ok);
        return res;
    }

    @PostMapping("/admin/refresh-match-times")
    public Map<String, Object> refreshMatchTimes(@RequestParam(required = false) String zone, @RequestParam(defaultValue = "false") boolean force) {
        java.time.ZoneId z = (zone == null || zone.isBlank()) ? java.time.ZoneId.systemDefault() : java.time.ZoneId.of(zone);
        int updated = service.refreshMatchTimes(z, force);
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("updated", updated);
        r.put("zone", z.toString());
        r.put("force", force);
        return r;
    }
}