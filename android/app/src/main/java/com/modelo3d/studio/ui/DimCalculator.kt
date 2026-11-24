package com.modelo3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelo3d.studio.model.LengthUnit

@Composable
private fun RowScope.KeyBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp)).background(CadColors.PanelHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RowScope.KeyText(text: String, size: Int = 22, onClick: () -> Unit) {
    KeyBtn(onClick) { Text(text, color = CadColors.Text, fontSize = size.sp) }
}

/** Calculadora numerica de cotas (como la de Onshape). Devuelve el valor en cm. */
@Composable
fun DimCalculator(
    initial: String,
    unit: LengthUnit,
    onUnit: (LengthUnit) -> Unit,
    isFixed: Boolean,
    onFree: () -> Unit,
    showMove: Boolean,
    moveStart: Boolean,
    onToggleMove: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onAccept: (Float) -> Unit,
    fixedUnit: String? = null,
    showDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    var txt by remember(initial) { mutableStateOf(initial) }
    var fresh by remember(initial) { mutableStateOf(true) }
    var sys by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }

    fun type(c: String) {
        if (fresh) {
            txt = if (c == ".") "0." else c
            fresh = false
            return
        }
        if (c == "." && txt.contains('.')) return
        txt = if (txt == "0" && c != ".") c else txt + c
    }

    Card(modifier.width(300.dp), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Valor y unidad
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(CadColors.PanelHi).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (sys) {
                        OutlinedTextField(
                            value = txt,
                            onValueChange = { txt = it; fresh = false },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                txt.ifEmpty { "0" }, color = CadColors.Text, fontSize = 24.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                                    .clickable { txt = ""; fresh = false },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", color = CadColors.TextDim, fontSize = 16.sp) }
                        }
                    }
                }
                Box(
                    Modifier.width(76.dp).height(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(CadColors.PanelHi).clickable(enabled = fixedUnit == null) { unitMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(fixedUnit ?: "${unit.label} ▾", color = CadColors.GoldMain, fontSize = 18.sp)
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        LengthUnit.values().forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u.label) },
                                onClick = { unitMenu = false; onUnit(u) }
                            )
                        }
                    }
                }
            }

            // Teclado numerico
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyText("7") { type("7") }
                KeyText("8") { type("8") }
                KeyText("9") { type("9") }
                KeyText("⌫") { fresh = false; txt = txt.dropLast(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyText("4") { type("4") }
                KeyText("5") { type("5") }
                KeyText("6") { type("6") }
                KeyText("ABC", 15) { sys = !sys }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyText("1") { type("1") }
                KeyText("2") { type("2") }
                KeyText("3") { type("3") }
                if (showDelete) {
                    KeyBtn(onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFE53935))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyText(".") { type(".") }
                KeyText("0") { type("0") }
                KeyText("+/-", 18) {
                    fresh = false
                    txt = if (txt.startsWith("-")) txt.drop(1) else "-$txt"
                }
                if (showMove) {
                    KeyText(if (moveStart) "Mueve inicio" else "Mueve final", 11) { onToggleMove() }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            if (isFixed) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF4CD964), RoundedCornerShape(8.dp))
                        .clickable(onClick = onFree),
                    contentAlignment = Alignment.Center
                ) { Text("Cota fija · toca para liberarla", color = Color(0xFF4CD964), fontSize = 13.sp) }
            }

            // Cancelar / aceptar
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(CadColors.Panel).border(1.dp, CadColors.GoldSoft, RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFE53935)) }
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E8E3E))
                        .clickable {
                            val n = txt.replace(',', '.').toFloatOrNull()
                            if (n != null && n > 0f) onAccept(n * (if (fixedUnit != null) 1f else unit.toCm))
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Check, contentDescription = "Aceptar", tint = Color.White) }
            }
        }
    }
}
