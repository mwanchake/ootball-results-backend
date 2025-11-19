package com.mwancha.footbal_api.dto;

import java.io.Serializable;

public record MatchDto(
    Long id,
    String league,
    String home,
    String away,
    String homeLogo,
    String awayLogo,
    Integer homeGoals,
    Integer awayGoals,
    String matchDate,
    String matchTime
) implements Serializable {}