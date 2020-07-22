package com.hines.james;

import lombok.Data;

@Data
public class ChallengerStreamDTO {
    private String username;
    private int wins;
    private int losses;
    private int draws;
    private PlayerStatus playerStatus;
}
