package ro.hack4arad.topul.ui.reports

import androidx.compose.material.icons.rounded.Landslide
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Domain
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Flood
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LightbulbCircle
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Un tip de sesizare/alertă. Codurile sunt aceleași ca pe server (/reports/categories). */
data class ReportKind(val code: String, val label: String, val icon: ImageVector, val urgent: Boolean = false)

val REPORT_KINDS = listOf(
    ReportKind("groapa", "Groapă în drum", Icons.Rounded.Construction),
    ReportKind("furtuna", "Copac căzut / furtună", Icons.Rounded.Forest, urgent = true),
    ReportKind("inundatie", "Apă / inundație", Icons.Rounded.Water, urgent = true),
    ReportKind("canal", "Capac de canal lipsă", Icons.Rounded.Warning, urgent = true),
    ReportKind("semnalizare", "Semn sau semafor", Icons.Rounded.Traffic, urgent = true),
    ReportKind("iluminat", "Iluminat stricat", Icons.Rounded.LightbulbCircle),
    ReportKind("trotuar", "Trotuar stricat", Icons.Rounded.DirectionsWalk),
    ReportKind("vandalism", "Vandalism", Icons.Rounded.Block),
    ReportKind("gunoi", "Gunoi / mizerie", Icons.Rounded.Delete),
    ReportKind("parcare", "Parcare ilegală", Icons.Rounded.LocalParking),
    ReportKind("animale", "Animale fără stăpân", Icons.Rounded.Pets),
    ReportKind("altele", "Altceva", Icons.Rounded.HelpOutline),
)

/** Alertele majore (din „Alerte urgente”): pericol pentru vieți, prioritate maximă la Primărie. */
val EMERGENCY_KINDS = listOf(
    ReportKind("inundatie_majora", "Inundație majoră / Mureșul", Icons.Rounded.Flood, urgent = true),
    ReportKind("gaze", "Scurgere de gaze", Icons.Rounded.Warning, urgent = true),
    ReportKind("explozie", "Explozie / incendiu", Icons.Rounded.LocalFireDepartment, urgent = true),
    ReportKind("cutremur", "Cutremur – clădire avariată", Icons.Rounded.Apartment, urgent = true),
    ReportKind("prabusire", "Clădire / structură prăbușită", Icons.Rounded.Domain, urgent = true),
    ReportKind("cablu", "Cablu electric căzut", Icons.Rounded.ElectricBolt, urgent = true),
    ReportKind("alunecare", "Surpare de teren / drum", Icons.Rounded.Landslide, urgent = true),
)

fun reportKind(code: String): ReportKind = (REPORT_KINDS + EMERGENCY_KINDS).firstOrNull { it.code == code } ?: REPORT_KINDS.last()

/** Culoarea statusului: roșu = nouă, portocaliu = în lucru, verde = rezolvată, gri = respinsă. */
fun statusColor(status: String): Color = when (status) {
    "rezolvat" -> Color(0xFF2EE6A6)
    "in_lucru" -> Color(0xFFFF8A3D)
    "respins" -> Color(0xFF7A80A8)
    else -> Color(0xFFFF3D81)
}
