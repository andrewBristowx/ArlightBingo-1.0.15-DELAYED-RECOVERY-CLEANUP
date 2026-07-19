package com.arlight.bingo.game;

public enum WinCondition {
    POINTS,   // gana quien tenga mas casillas completadas cuando se acaba el tiempo (1 punto por casilla)
    LINE,
    FULL_CARD,
    BLACKOUT // alias de FULL_CARD, incluido por claridad de nombre para los admins
}
