package com.mwancha.footbal_api;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "matches")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "league")
    private String league;
    
    // Database uses hometeam/awayteam column names
    @Column(name = "hometeam")
    private String home;

    @Column(name = "awayteam")
    private String away;

    @Column(name = "hometeam_goals")
    private Integer homeGoals;

    @Column(name = "awayteam_goals")
    private Integer awayGoals;

    // kickoff column stores the match datetime
    @Column(name = "kickoff")
    private String matchDate;
}