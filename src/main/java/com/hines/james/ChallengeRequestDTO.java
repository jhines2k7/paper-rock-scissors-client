package com.hines.james;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChallengeRequestDTO {
    private int playerId;
    private String clientId;
}
