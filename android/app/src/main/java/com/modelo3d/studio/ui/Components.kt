package com.modelo3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(CadColors.GoldLight, CadColors.GoldDeep)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = CadColors.OnGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, lineHeight = 15.sp
        )
    }
}

@Composable
fun FrameButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(CadColors.Panel)
            .border(1.dp, CadColors.GoldSoft, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun ToolTab(kind: IconKind, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val tint = if (selected) CadColors.GoldMain else CadColors.Text
    Column(
        modifier
            .padding(3.dp)
            .clip(shape)
            .background(if (selected) CadColors.GoldFill else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, CadColors.GoldMain, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CadIcon(kind, tint, 26.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun RailButton(
    selected: Boolean,
    onClick: () -> Unit,
    kind: IconKind? = null,
    glyph: String? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(10.dp)
    val tint = when {
        !enabled -> CadColors.TextDim.copy(alpha = 0.35f)
        selected -> CadColors.GoldMain
        else -> CadColors.Text
    }
    Box(
        Modifier
            .size(50.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) CadColors.GoldFill else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, CadColors.GoldMain, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (kind != null) CadIcon(kind, tint, 26.dp)
        if (glyph != null) Text(glyph, color = tint, fontSize = 22.sp)
    }
}

@Composable
fun PlaneChip(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val parts = com.modelo3d.studio.model.planeLabel(name).split(" ")
    val tint = if (selected) CadColors.GoldMain else CadColors.Text
    Row(
        modifier
            .clip(shape)
            .background(if (selected) CadColors.GoldFill else CadColors.Panel)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) CadColors.GoldMain else CadColors.GoldSoft, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CadIcon(IconKind.Plane, tint, 22.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(parts[0], color = tint, fontSize = 13.sp, maxLines = 1)
            Text(parts.getOrElse(1) { "" }, color = tint, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun PillButton(kind: IconKind, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(27.dp)
    Row(
        modifier
            .height(54.dp)
            .clip(shape)
            .background(CadColors.Panel)
            .border(1.dp, CadColors.GoldMain, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CadIcon(kind, CadColors.GoldMain, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = CadColors.Text, fontSize = 14.sp)
    }
}

@Composable
fun BarIcon(
    kind: IconKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val tint = when {
        !enabled -> CadColors.TextDim.copy(alpha = 0.35f)
        accent -> CadColors.GoldMain
        else -> CadColors.Text
    }
    Box(
        modifier.fillMaxHeight().clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { CadIcon(kind, tint, 26.dp) }
}

@Composable
fun BarDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(CadColors.GoldSoft))
}
