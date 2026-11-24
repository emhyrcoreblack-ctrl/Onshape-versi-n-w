package com.modelo3d.studio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelo3d.studio.model.SketchTool

private class ToolDef(val label: String, val icon: IconKind, val tool: SketchTool?)

private fun soon(label: String) = ToolDef(label, IconKind.ToolSoon, null)

private val GROUPS: List<List<ToolDef>> = listOf(
    listOf(ToolDef("Línea", IconKind.ToolLine, SketchTool.Line)),
    listOf(
        ToolDef("Rectángulo por esquina", IconKind.ToolRect, SketchTool.RectCorner),
        ToolDef("Rectángulo por punto central", IconKind.ToolRectCenter, SketchTool.RectCenter),
        ToolDef("Círculo por punto central", IconKind.ToolCircle, SketchTool.Circle),
        ToolDef("Círculo por 3 puntos", IconKind.ToolCircle3, SketchTool.Circle3),
        soon("Elipse"), soon("Polígono inscrito"), soon("Polígono circunscrito")
    ),
    listOf(
        soon("Texto"), soon("Usar"), soon("Intersección"), soon("Redondeo"),
        soon("Chaflán"), soon("Insertar DXF o DWG"), soon("Insertar imagen")
    ),
    listOf(ToolDef("Cota", IconKind.ToolDim, SketchTool.Dimension)),
    listOf(
        ToolDef("Arco por 3 puntos", IconKind.ToolArc, SketchTool.Arc3),
        soon("Arco tangente"), soon("Arco por punto central")
    ),
    listOf(soon("Spline"), soon("Punto de spline"), soon("Punto")),
    listOf(soon("Recortar"), soon("Extender"), soon("Partir"), soon("Equidistancia"), soon("Ranura")),
    listOf(soon("Patrón lineal"), soon("Simetría"), soon("Patrón circular"), soon("Transformar")),
    listOf(
        soon("Coincidente"), soon("Concéntrico"), soon("Paralelo"), soon("Tangente"),
        soon("Horizontal"), soon("Vertical"), soon("Perpendicular"), soon("Igual"),
        soon("Punto medio"), soon("Normal"), soon("Perforar"), soon("Simétrico"), soon("Fijar")
    )
)

@Composable
private fun ToolCell(def: ToolDef, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ready = def.tool != null
    val tint = when {
        selected -> CadColors.GoldMain
        ready -> CadColors.Text
        else -> CadColors.TextDim.copy(alpha = 0.55f)
    }
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CadIcon(def.icon, tint, 28.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            def.label, color = tint, fontSize = 11.sp, lineHeight = 13.sp,
            textAlign = TextAlign.Center, maxLines = 3
        )
    }
}

/** Catálogo de herramientas de boceto (como "Herramientas de búsqueda" de Onshape). */
@Composable
fun ToolCatalog(
    active: SketchTool?,
    onPick: (SketchTool) -> Unit,
    onSoon: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.heightIn(max = 430.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Herramientas de búsqueda") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = CadColors.Text, fontSize = 16.sp) }
            }
            HorizontalDivider(color = CadColors.GoldSoft)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                GROUPS.forEach { group ->
                    val items = group.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                    if (items.isNotEmpty()) {
                        items.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { d ->
                                    ToolCell(d, d.tool != null && d.tool == active, Modifier.weight(1f)) {
                                        val t = d.tool
                                        if (t != null) onPick(t) else onSoon(d.label)
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = CadColors.GoldSoft)
                    }
                }
            }
        }
    }
}
