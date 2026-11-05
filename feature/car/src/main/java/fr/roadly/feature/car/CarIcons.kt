package fr.roadly.feature.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.graphics.drawable.IconCompat
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.model.ManeuverType

/**
 * Icônes des gabarits de la voiture : les icônes Material du téléphone (ImageVector) dessinées en bitmaps, sans composition.
 * Mises en cache par icône, taille et couleur (les gabarits sont reconstruits à chaque position).
 */
internal object CarIcons {
    private const val SIZE_PX = 128
    private val cache = HashMap<Triple<ImageVector, Int, Int>, Bitmap>()

    fun maneuver(type: ManeuverType): CarIcon = of(type.icon())

    /** [tint] : teinte demandée à l'hôte (actions et lignes) ; null = bitmap tel quel (manœuvres, blanc sur la carte de guidage). */
    fun of(vector: ImageVector, color: Int = Color.WHITE, tint: CarColor? = null): CarIcon {
        val builder = CarIcon.Builder(IconCompat.createWithBitmap(bitmap(vector, SIZE_PX, color)))
        tint?.let(builder::setTint)
        return builder.build()
    }

    @Synchronized
    fun bitmap(vector: ImageVector, sizePx: Int, color: Int): Bitmap = cache.getOrPut(Triple(vector, sizePx, color)) {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        draw(vector.root, canvas, paint)
        bmp
    }

    private fun draw(group: VectorGroup, canvas: Canvas, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorPath -> {
                    val path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    canvas.drawPath(path, paint)
                }
                is VectorGroup -> draw(node, canvas, paint)
            }
        }
        canvas.restore()
    }
}
