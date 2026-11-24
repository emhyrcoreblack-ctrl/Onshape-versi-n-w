package com.modelo3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelo3d.studio.model.*

private fun String.toF(): Float? = replace(',', '.').toFloatOrNull()

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** Panel inferior azul con ✓ y ✕, como "Boceto 1" / "Extrusión 1" en Onshape. */
@Composable
private fun PanelFrame(
    title: String,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(CadColors.GoldLight, CadColors.GoldDeep))).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), color = CadColors.OnGold, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onConfirm, enabled = canConfirm) {
                Icon(Icons.Default.Check, "Aceptar", tint = if (canConfirm) CadColors.OnGold else Color(0x661A1405))
            }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancelar", tint = CadColors.OnGold) }
        }
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun SketchPanel(
    draft: SketchOp,
    isNew: Boolean,
    toolName: String?,
    hint: String,
    canFinish: Boolean,
    facePlane: String?,
    onPlaneChange: (String) -> Unit,
    offsetOn: Boolean,
    onToggleOffset: () -> Unit,
    onTools: () -> Unit,
    onUndoShape: () -> Unit,
    onFinish: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelFrame(draft.name, draft.shapes.isNotEmpty(), onConfirm, onCancel, modifier) {
        var planeMenu by remember { mutableStateOf(false) }
        Box {
            Text(
                "Plano: ${planeLabel(draft.plane)} ▾ · ${draft.shapes.size} elementos",
                style = MaterialTheme.typography.labelLarge,
                color = CadColors.GoldMain,
                modifier = Modifier.clickable { planeMenu = true }.padding(vertical = 6.dp)
            )
            DropdownMenu(expanded = planeMenu, onDismissRequest = { planeMenu = false }) {
                PLANES.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(planeLabel(p)) },
                        onClick = { planeMenu = false; onPlaneChange(p) }
                    )
                }
                if (facePlane != null) {
                    DropdownMenuItem(
                        text = { Text("Cara seleccionada") },
                        onClick = { planeMenu = false; onPlaneChange(facePlane) }
                    )
                }
            }
        }
        Text(
            if (toolName != null) "$toolName: $hint" else hint,
            color = CadColors.TextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onTools) { Text("Herramientas") }
            OutlinedButton(onClick = onUndoShape, enabled = draft.shapes.isNotEmpty()) { Text("Quitar último") }
            OutlinedButton(onClick = onToggleOffset) { Text(if (offsetOn) "Cursor elevado: sí" else "Cursor elevado: no") }
            if (canFinish) OutlinedButton(onClick = onFinish) { Text("Terminar trazo") }
        }
        if (!isNew) TextButton(onClick = onDelete) { Text("Eliminar boceto") }
    }
}

@Composable
fun DimDialog(shape: Shape, onApply: (Shape) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var f1 by remember(shape) {
        mutableStateOf(
            when (shape) {
                is RectS -> fmt(shape.w)
                is CircleS -> fmt(shape.r)
                is LineS -> fmt(dist(shape.a, shape.b))
                is ArcS -> ""
            }
        )
    }
    var f2 by remember(shape) { mutableStateOf(if (shape is RectS) fmt(shape.h) else "") }
    var moveStart by remember(shape) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (shape) {
                    is RectS -> {
                        NumberField("Ancho (cm)", f1, { f1 = it }, Modifier.fillMaxWidth())
                        NumberField("Alto (cm)", f2, { f2 = it }, Modifier.fillMaxWidth())
                    }
                    is CircleS -> NumberField("Radio (cm)", f1, { f1 = it }, Modifier.fillMaxWidth())
                    is LineS -> {
                        NumberField("Longitud (cm)", f1, { f1 = it }, Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !moveStart, onClick = { moveStart = false })
                            Text("Mover el extremo final")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = moveStart, onClick = { moveStart = true })
                            Text("Mover el extremo inicial")
                        }
                        Text(
                            "Las líneas conectadas a ese punto se mueven con él y el contorno sigue cerrado.",
                            color = CadColors.TextDim, fontSize = 12.sp, lineHeight = 14.sp
                        )
                    }
                    is ArcS -> Text("Los arcos aún no se pueden acotar.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = f1.toF()
                val b = f2.toF()
                val out: Shape? = when (shape) {
                    is RectS -> if (a != null && b != null && a > 0f && b > 0f) shape.copy(w = a, h = b) else null
                    is CircleS -> if (a != null && a > 0f) shape.copy(r = a) else null
                    is LineS -> {
                        val len = dist(shape.a, shape.b)
                        if (a != null && a > 0f && len > 1e-6f) {
                            val k = a / len
                            if (moveStart) {
                                shape.copy(
                                    a = Pt(
                                        shape.b.u + (shape.a.u - shape.b.u) * k,
                                        shape.b.v + (shape.a.v - shape.b.v) * k
                                    )
                                )
                            } else {
                                shape.copy(
                                    b = Pt(
                                        shape.a.u + (shape.b.u - shape.a.u) * k,
                                        shape.a.v + (shape.b.v - shape.a.v) * k
                                    )
                                )
                            }
                        } else null
                    }
                    is ArcS -> null
                }
                if (out != null) onApply(out)
            }) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Eliminar") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}

@Composable
private fun TabsRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        labels.forEachIndexed { i, l ->
            Column(Modifier.clickable { onSelect(i) }) {
                Text(
                    l,
                    color = if (i == selected) CadColors.GoldMain else CadColors.TextDim,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.height(3.dp).fillMaxWidth()
                        .background(if (i == selected) CadColors.GoldMain else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = CadColors.Text, fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = CadColors.Text, fontSize = 15.sp)
        Text(value, color = CadColors.GoldMain, fontSize = 17.sp)
        Text("  ⇔", color = CadColors.GoldMain, fontSize = 16.sp)
    }
}

@Composable
private fun ChevronRow(title: String, sub: String, highlight: Boolean, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (highlight) CadColors.GoldFill else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CadColors.TextDim, fontSize = 13.sp)
            Text(sub, color = CadColors.Text, fontSize = 16.sp)
        }
        Text(if (expanded) "▾" else "›", color = CadColors.GoldMain, fontSize = 22.sp)
    }
}

/** Ventana de extrusión con las opciones de Onshape. */
@Composable
fun ExtrudePanel(
    draft: ExtrudeOp,
    sketchName: String,
    regionCount: Int,
    isNew: Boolean,
    onChange: (ExtrudeOp) -> Unit,
    onEdit: (String) -> Unit,
    onSoon: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var regionsOpen by remember { mutableStateOf(false) }
    var endMenu by remember { mutableStateOf(false) }
    var dirMenu by remember { mutableStateOf(false) }
    val selRegions = draft.regions ?: (0 until regionCount).toList()

    PanelFrame(draft.name, draft.depth > 0f && selRegions.isNotEmpty(), onConfirm, onCancel, modifier) {
        Column(
            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabsRow(listOf("Sólido", "Superficie", "Delgado"), draft.kind.ordinal) {
                onChange(draft.copy(kind = ExtrudeKind.values()[it]))
            }
            TabsRow(listOf("Nuevo", "Añadir", "Quitar", "Intersección"), draft.op.ordinal) {
                onChange(draft.copy(op = BoolOp.values()[it]))
            }
            ChevronRow(
                "Caras y regiones de boceto para extrusión",
                if (selRegions.size == 1) "1 entidad seleccionada" else "${selRegions.size} entidades seleccionadas",
                true, regionsOpen
            ) { regionsOpen = !regionsOpen }
            if (regionsOpen) {
                for (i in 0 until regionCount) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val nl = if (i in selRegions) selRegions - i else (selRegions + i).sorted()
                            if (nl.isNotEmpty()) {
                                onChange(draft.copy(regions = if (nl.size == regionCount) null else nl))
                            }
                        }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = i in selRegions, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Región ${i + 1}", color = CadColors.Text, fontSize = 15.sp)
                    }
                }
            }
            val endNames = listOf("Ciego", "Hasta el siguiente", "Hasta pieza", "Hasta cara", "Hasta vértice")
            Box {
                ChevronRow("Tipo de extremo", endNames[draft.endType.ordinal], false, false) { endMenu = true }
                DropdownMenu(expanded = endMenu, onDismissRequest = { endMenu = false }) {
                    endNames.forEachIndexed { i, t ->
                        DropdownMenuItem(
                            text = { Text((if (draft.endType.ordinal == i) "✓ " else "") + t) },
                            onClick = {
                                endMenu = false
                                onChange(draft.copy(endType = EndType.values()[i], endRef = null))
                            }
                        )
                    }
                }
            }
            if (draft.endType == EndType.ToPart || draft.endType == EndType.ToFace || draft.endType == EndType.ToVertex) {
                val what = when (draft.endType) {
                    EndType.ToPart -> "una pieza"
                    EndType.ToFace -> "una cara"
                    else -> "un vértice"
                }
                ChevronRow(
                    "Selección",
                    if (draft.endRef == null) "Toca $what en la escena"
                    else if (draft.endType == EndType.ToPart) "Pieza: ${draft.endRef}"
                    else if (draft.endType == EndType.ToFace) "1 cara seleccionada"
                    else "1 vértice seleccionado",
                    true, false
                ) { }
            }
            SwitchRow("Dirección opuesta", draft.reverse) { onChange(draft.copy(reverse = it)) }
            if (draft.endType == EndType.Blind) {
                ValueRow("Profundidad", "${fmt(draft.depth)} cm") { onEdit("depth") }
            } else {
                Text(
                    "Profundidad automática según el extremo elegido",
                    color = CadColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (draft.kind == ExtrudeKind.Thin) {
                ValueRow("Espesor", "${fmt(draft.thickness)} cm") { onEdit("thick") }
                SwitchRow("Espesor hacia afuera", draft.thinOut) { onChange(draft.copy(thinOut = it)) }
            }
            val dirNames = listOf("Normal al plano", "Eje X", "Eje Y", "Eje Z")
            Box {
                ChevronRow("Dirección", dirNames[draft.dirAxis.coerceIn(0, 3)], false, false) { dirMenu = true }
                DropdownMenu(expanded = dirMenu, onDismissRequest = { dirMenu = false }) {
                    dirNames.forEachIndexed { i, t ->
                        DropdownMenuItem(
                            text = { Text((if (draft.dirAxis == i) "✓ " else "") + t) },
                            onClick = { dirMenu = false; onChange(draft.copy(dirAxis = i)) }
                        )
                    }
                }
            }
            SwitchRow("Desfase inicial", draft.offOn) { onChange(draft.copy(offOn = it)) }
            if (draft.offOn) ValueRow("Distancia de desfase", "${fmt(draft.startOffset)} cm") { onEdit("offset") }
            SwitchRow("Simétrico", draft.symmetric) { onChange(draft.copy(symmetric = it)) }
            SwitchRow("Desmoldeo", draft.dfOn) { onChange(draft.copy(dfOn = it)) }
            if (draft.dfOn) ValueRow("Ángulo de desmoldeo", "${fmt(draft.draftDeg)}°") { onEdit("draft") }
            SwitchRow("Segunda posición final", draft.secOn) { onChange(draft.copy(secOn = it)) }
            if (draft.secOn) ValueRow("Profundidad 2", "${fmt(draft.second)} cm") { onEdit("second") }
            if (!isNew) TextButton(onClick = onDelete) { Text("Eliminar extrusión") }
        }
    }
}
