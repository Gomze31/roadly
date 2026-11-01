package fr.roadly.core.model

/**
 * E2 : inclinaison de croisière en vue 3D, en degrés. L'approche reste à 50°, l'économie d'énergie à 45° ; « ville dense » (60°) seulement
 * avec STANDARD ou HIGH. LIMITE : 60° au plus, maximum rendu par MapLibre Android 11.13.5 (la spécification demandait 65°).
 */
enum class PitchPreset(val degrees: Double) { LOW(45.0), STANDARD(58.0), HIGH(60.0) }
