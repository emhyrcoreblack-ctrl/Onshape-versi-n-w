package com.modelo3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelo3d.studio.model.*

@Composable
private fun TreeRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    shown: Boolean?,
    onToggle: () -> Unit,
    onClick: (() -> Unit)?,
    menu: List<Pair<String, () -> Unit>>
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = CadColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = CadColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (shown != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                CadIcon(
                    if (shown) IconKind.Eye else IconKind.EyeOff,
                    if (shown) CadColors.GoldMain else CadColors.TextDim, 22.dp
                )
            }
        }
        if (menu.isNotEmpty()) {
            Box {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) { Text("⋮", color = CadColors.Text, fontSize = 20.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
                    }
                }
            }
        }
    }
}

/** Árbol dividido: arriba planos y operaciones; abajo las piezas numeradas. */
@Composable
fun FeatureTree(
    ops: List<Op>,
    parts: List<Solid>,
    visible: MutableMap<String, Boolean>,
    hidden: MutableMap<String, Boolean>,
    onEdit: (Op) -> Unit,
    onDelete: (Op) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var frac by remember { mutableFloatStateOf(0.6f) }
    var height by remember { mutableIntStateOf(1) }
    var geoOpen by remember { mutableStateOf(true) }

    Card(modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxSize().onSizeChanged { height = it.height }) {

            // ---------- Parte de arriba: planos y operaciones ----------
            Column(Modifier.weight(frac).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Operaciones (${ops.size})", Modifier.weight(1f),
                        color = CadColors.GoldMain, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) { Text("✕", color = CadColors.Text, fontSize = 16.sp) }
                }

                Row(
                    Modifier.fillMaxWidth().clickable { geoOpen = !geoOpen }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (geoOpen) "▾" else "▸", color = CadColors.GoldMain, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Geometría por defecto", color = CadColors.Text, fontSize = 13.sp)
                }
                if (geoOpen) {
                    listOf(
                        "Origin" to "Origen", "Top" to "Superior (XY)",
                        "Front" to "Frontal (XZ)", "Right" to "Derecho (YZ)"
                    ).forEach { (key, label) ->
                        TreeRow(
                            leading = {
                                CadIcon(
                                    if (key == "Origin") IconKind.Origin else IconKind.Plane,
                                    CadColors.TextDim, 20.dp
                                )
                            },
                            title = label,
                            subtitle = null,
                            shown = visible[key] != false,
                            onToggle = { visible[key] = visible[key] == false },
                            onClick = null,
                            menu = emptyList()
                        )
                    }
                }

                if (ops.isEmpty()) {
                    Text(
                        "Aún no hay operaciones. Usa Boceto y Extruir.",
                        Modifier.padding(12.dp), color = CadColors.TextDim, fontSize = 12.sp
                    )
                }
                ops.forEach { op ->
                    val sub = when (op) {
                        is SketchOp -> "${planeLabel(op.plane)} · ${op.shapes.size} elementos"
                        is ExtrudeOp -> listOf("Nuevo", "Añadir", "Quitar", "Intersección")[op.op.ordinal] +
                            (if (op.endType == EndType.Blind) " · ${fmt(op.depth)} cm" else " · hasta extremo") +
                            (if (op.reverse) " · inversa" else "")
                    }
                    TreeRow(
                        leading = {
                            CadIcon(
                                if (op is SketchOp) IconKind.Pencil else IconKind.Extrude,
                                CadColors.GoldMain, 20.dp
                            )
                        },
                        title = op.name,
                        subtitle = sub,
                        shown = hidden[op.id] != true,
                        onToggle = { hidden[op.id] = hidden[op.id] != true },
                        onClick = { onEdit(op) },
                        menu = listOf("Editar" to { onEdit(op) }, "Eliminar" to { onDelete(op) })
                    )
                }
            }

            // ---------- Asa para cambiar el tamaño de cada mitad ----------
            Box(
                Modifier.fillMaxWidth().height(24.dp).background(CadColors.PanelHi).pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        frac = (frac + dy / height).coerceIn(0.2f, 0.8f)
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(width = 44.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(CadColors.GoldSoft)
                )
            }

            // ---------- Parte de abajo: piezas numeradas ----------
            Column(Modifier.weight(1f - frac).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Piezas (${parts.size})",
                    Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                    color = CadColors.GoldMain, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
                if (parts.isEmpty()) {
                    Text(
                        "Las piezas extruidas aparecerán aquí.",
                        Modifier.padding(12.dp), color = CadColors.TextDim, fontSize = 12.sp
                    )
                }
                parts.forEachIndexed { i, s ->
                    val op = ops.firstOrNull { it.id == s.opId }
                    val d = sizeOf(listOf(s))
                    TreeRow(
                        leading = {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(CadColors.GoldFill),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", color = CadColors.GoldMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        title = s.name,
                        subtitle = if (d != null) "${fmt(d.x)} × ${fmt(d.y)} × ${fmt(d.z)} cm" else null,
                        shown = hidden[s.opId] != true,
                        onToggle = { hidden[s.opId] = hidden[s.opId] != true },
                        onClick = { if (op != null) onEdit(op) },
                        menu = if (op != null) {
                            listOf("Editar extrusión" to { onEdit(op) }, "Eliminar pieza" to { onDelete(op) })
                        } else emptyList()
                    )
                }
            }
        }
    }
}
