package fr.roadly.core.model

/**
 * C1 : mode de caméra du guidage, choisi pour la session (bouton 2D/3D, « nord en haut »). Départ : réglages E2 (vue au départ, rotation).
 * Dans `core:model` (E2) : persisté par `core:data`, qui ne dépend pas de `core:map`.
 */
enum class CameraMode3D {
    FOLLOW_3D, FOLLOW_2D, NORTH_UP;

    /** Bouton 2D/3D : 3D → 2D, sinon 3D (depuis « nord en haut », on repasse en 3D). */
    fun toggled3d(): CameraMode3D = if (this == FOLLOW_3D) FOLLOW_2D else FOLLOW_3D

    companion object {
        /** Ancien réglage « Carte 3D en guidage » (clé `map.3d`) : activé → 3D, sinon 2D. Sert de vue au départ tant qu'elle n'a pas été choisie (migration E2). */
        fun default(map3dEnabled: Boolean): CameraMode3D = if (map3dEnabled) FOLLOW_3D else FOLLOW_2D

        /** E2 : mode de départ d'une session — la vue choisie [view], ou nord en haut si la rotation automatique est coupée. */
        fun initial(view: CameraMode3D, autoRotate: Boolean): CameraMode3D = if (autoRotate) view else NORTH_UP
    }
}
