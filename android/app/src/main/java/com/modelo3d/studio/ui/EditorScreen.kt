package com.modelo3d.studio.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelo3d.studio.model.*
import com.modelo3d.studio.viewer.FacePick
import com.modelo3d.studio.viewer.NavMode
import com.modelo3d.studio.viewer.OrbitCamera
import com.modelo3d.studio.viewer.ShadeMode
import com.modelo3d.studio.viewer.ViewCube
import com.modelo3d.studio.viewer.Viewport
import com.modelo3d.studio.viewer.pickBody
import com.modelo3d.studio.viewer.pickFace
import com.modelo3d.studio.viewer.pickPlaneAt
import com.modelo3d.studio.viewer.pickVertex
import java.util.UUID
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max

private sealed interface Mode {
    object Idle : Mode
    data class Sketching(val draft: SketchOp, val isNew: Boolean) : Mode
    data class Extruding(val draft: ExtrudeOp, val isNew: Boolean) : Mode
}

private fun newId(): String = UUID.randomUUID().toString().take(8)

@Composable
fun EditorScreen(project: Project, store: ProjectStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val camera = remember { OrbitCamera() }
    val doc = remember(project.id) { CadDocument(project.ops) }
    val visible = remember {
        mutableStateMapOf("Origin" to true, "Top" to true, "Front" to true, "Right" to true)
    }
    val hidden = remember { mutableStateMapOf<String, Boolean>() }
    var mode by remember { mutableStateOf<Mode>(Mode.Idle) }
    var panelOpen by remember { mutableStateOf(false) }
    var lastPlane by remember { mutableStateOf("Front") }
    var menuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var propsOpen by remember { mutableStateOf(false) }

    // Sesión de dibujo del boceto
    var tool by remember { mutableStateOf<SketchTool?>(null) }
    var pending by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var cursor by remember { mutableStateOf<Pt?>(null) }
    var catalogOpen by remember { mutableStateOf(false) }
    var dimTarget by remember { mutableStateOf<DimRef?>(null) }
    var moveStartLine by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(LengthUnit.Cm) }
    var extCalc by remember { mutableStateOf<String?>(null) }
    var quadFixed by remember { mutableStateOf(10f) }
    var sUndo by remember { mutableStateOf<List<List<Shape>>>(emptyList()) }
    var sRedo by remember { mutableStateOf<List<List<Shape>>>(emptyList()) }
    var lockBefore by remember { mutableStateOf(false) }
    var selFace by remember { mutableStateOf<FacePick?>(null) }
    var offsetCursor by remember { mutableStateOf(true) }
    var viewMenu by remember { mutableStateOf(false) }
    var shade by remember { mutableStateOf(ShadeMode.Shaded) }
    var selPlane by remember { mutableStateOf<String?>(null) }
    var chainStart by remember { mutableStateOf<Pt?>(null) }
    var snapRes by remember { mutableStateOf<SnapResult?>(null) }

    LaunchedEffect(doc.ops) {
        selFace = null
        project.ops = doc.ops
        store.save(project)
    }

    // El borrador en edición se aplica en vivo sobre las operaciones.
    val m = mode
    val viewOps: List<Op> = when (m) {
        is Mode.Sketching -> doc.ops.upserted(m.draft)
        is Mode.Extruding -> doc.ops.upserted(m.draft)
        Mode.Idle -> doc.ops
    }
    val allSolids = remember(viewOps) { buildSolids(viewOps) }
    val solids = allSolids.filter { hidden[it.opId] != true }
    val used = viewOps.filterIsInstance<ExtrudeOp>().map { it.sketchId }.toSet()
    val sk = (m as? Mode.Sketching)?.draft
    val cur = cursor

    val polysOther = viewOps.filterIsInstance<SketchOp>()
        .filter { it.id !in used && it.id != sk?.id && hidden[it.id] != true }
        .flatMap { shapePolys(it.plane, it.shapes) }
    val previews = if (sk != null) previewShapes(tool, pending, cur) else emptyList()
    val activePolys = if (sk != null) shapePolys(sk.plane, sk.shapes) else emptyList()
    val previewPolys = if (sk != null) shapePolys(sk.plane, previews) else emptyList()
    val labels = if (sk != null) shapeLabels(sk.plane, previews) else emptyList()
    val dimsActive = if (sk != null) shapeDims(sk.plane, sk.shapes, dimTarget) else emptyList()
    val dimsOther = viewOps.filterIsInstance<SketchOp>()
        .filter { it.id !in used && it.id != sk?.id && hidden[it.id] != true }
        .flatMap { shapeDims(it.plane, it.shapes, null) }
    val dims = dimsActive + dimsOther
    val cursor3 = if (sk != null && cur != null && tool != null && tool != SketchTool.Dimension) {
        planePoint(sk.plane, cur.u, cur.v, 0f)
    } else null

    val sr = snapRes
    val drawing = sk != null && cur != null && tool != null && tool != SketchTool.Dimension
    val cursorSnap = drawing && sr != null && sr.magnet
    val hr = sr?.hRef
    val vr = sr?.vRef
    val guideH3 = if (drawing && sk != null && hr != null) planePoint(sk.plane, hr.u, hr.v, 0f) else null
    val guideV3 = if (drawing && sk != null && vr != null) planePoint(sk.plane, vr.u, vr.v, 0f) else null
    val snapLabel = if (drawing && sr != null) {
        when {
            sr.point -> "Imán: punto"
            sr.magnet -> "Imán: alineado"
            sr.hRef != null -> "Misma altura"
            sr.vRef != null -> "Misma vertical"
            else -> null
        }
    } else null

    val quadHalf = quadFixed

    val extent = sceneExtent(solids, (polysOther + activePolys + previewPolys).map { it.pts })
    val planeSize = 10f
    val gridHalf = max(12f, extent * 1.6f)

    fun isoFit(e: Float) {
        camera.setView(-35f, 25f)
        camera.fit(max(e, 10f))
    }

    // Orienta la cámara de frente a cualquier plano (nombrado o cara de pieza)
    fun viewFor(plane: String) {
        val d = planeDef(plane)
        val yaw = Math.toDegrees(atan2(-d.u.y.toDouble(), d.u.x.toDouble())).toFloat()
        val pitch = Math.toDegrees(asin(d.n.z.coerceIn(-1f, 1f).toDouble())).toFloat()
        camera.setView(yaw, pitch)
        camera.fit(25f)
        camera.centerOn(d.o)
    }

    fun beginSketchSession(plane: String) {
        lockBefore = camera.locked
        camera.locked = true
        tool = null
        pending = emptyList()
        cursor = null
        dimTarget = null
        catalogOpen = true
        chainStart = null
        sUndo = emptyList()
        sRedo = emptyList()
        viewFor(plane)
    }

    fun endSketchSession() {
        camera.locked = lockBefore
        tool = null
        pending = emptyList()
        cursor = null
        dimTarget = null
        catalogOpen = false
        chainStart = null
        sUndo = emptyList()
        sRedo = emptyList()
    }

    fun startSketch(plane: String) {
        if (plane in PLANES) lastPlane = plane
        quadFixed = max(10f, selFace?.half ?: 0f)
        val n = doc.ops.count { it is SketchOp } + 1
        mode = Mode.Sketching(SketchOp(newId(), "Boceto $n", plane, emptyList()), true)
        beginSketchSession(plane)
    }

    fun startExtrude() {
        extCalc = null
        val usedNow = doc.ops.filterIsInstance<ExtrudeOp>().map { it.sketchId }.toSet()
        val cand = doc.ops.filterIsInstance<SketchOp>()
            .lastOrNull { it.id !in usedNow && profiles(it.shapes).isNotEmpty() }
        if (cand == null) {
            Toast.makeText(context, "Dibuja primero un perfil cerrado", Toast.LENGTH_SHORT).show()
            return
        }
        val n = doc.ops.count { it is ExtrudeOp } + 1
        mode = Mode.Extruding(ExtrudeOp(newId(), "Extrusión $n", cand.id, 5f, false), true)
        isoFit(max(extent, 20f))
    }

    fun editOp(op: Op) {
        panelOpen = false
        when (op) {
            is SketchOp -> {
                var mx = 6f
                for (pt in keyPoints(op.shapes)) mx = max(mx, max(abs(pt.u), abs(pt.v)))
                quadFixed = max(10f, mx * 1.4f)
                mode = Mode.Sketching(op, false)
                beginSketchSession(op.plane)
            }
            is ExtrudeOp -> { mode = Mode.Extruding(op, false); isoFit(max(extent, 20f)) }
        }
    }

    // Todo cambio del boceto pasa por aquí para poder deshacerlo y rehacerlo
    fun setShapes(md: Mode.Sketching, shapes: List<Shape>) {
        sUndo = sUndo + listOf(md.draft.shapes)
        sRedo = emptyList()
        mode = md.copy(draft = md.draft.copy(shapes = shapes))
    }

    fun sketchUndo() {
        val md = mode as? Mode.Sketching ?: return
        if (sUndo.isEmpty()) return
        val prev = sUndo.last()
        sRedo = sRedo + listOf(md.draft.shapes)
        sUndo = sUndo.dropLast(1)
        pending = emptyList()
        chainStart = null
        cursor = null
        mode = md.copy(draft = md.draft.copy(shapes = prev))
    }

    fun sketchRedo() {
        val md = mode as? Mode.Sketching ?: return
        if (sRedo.isEmpty()) return
        val next = sRedo.last()
        sUndo = sUndo + listOf(md.draft.shapes)
        sRedo = sRedo.dropLast(1)
        pending = emptyList()
        chainStart = null
        cursor = null
        mode = md.copy(draft = md.draft.copy(shapes = next))
    }

    // Entrada táctil del dibujo: fase 0 = moviendo, fase 1 = soltó (coloca el punto)
    fun onSketchInput(phase: Int, u: Float, v: Float) {
        val md = mode as? Mode.Sketching ?: return
        val t = tool ?: return
        val draft = md.draft
        val raw = Pt(u, v)
        if (t == SketchTool.Dimension) {
            if (phase == 1) {
                val hit = hitDim(draft.shapes, raw, camera.zoom, true)
                if (hit != null) {
                    moveStartLine = false
                    catalogOpen = false
                    dimTarget = hit
                }
            }
            return
        }
        val si = snapInfo(raw, draft.shapes, pending, camera.zoom)
        val p = si.pt
        val prevSnap = snapRes
        if (si.magnet && (prevSnap == null || !prevSnap.magnet)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        snapRes = if (phase == 0) si else null
        cursor = p
        if (phase == 0) return
        val pts = pending + p
        if (pts.size >= t.needed) {
            val shape = shapeFrom(t, pts)
            if (shape != null) {
                setShapes(md, draft.shapes + shape)
                val cs = chainStart
                val closed = t == SketchTool.Line && cs != null && dist(p, cs) < 1e-4f
                if (closed) {
                    Toast.makeText(context, "Contorno cerrado", Toast.LENGTH_SHORT).show()
                    chainStart = null
                    pending = emptyList()
                } else {
                    pending = if (t == SketchTool.Line) listOf(p) else emptyList()
                }
            }
        } else {
            pending = pts
            if (t == SketchTool.Line) chainStart = p
        }
    }

    fun saveNow() {
        project.ops = doc.ops
        store.save(project)
        Toast.makeText(context, "Proyecto guardado", Toast.LENGTH_SHORT).show()
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(exportStl(buildSolids(doc.ops)).toByteArray())
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "STL exportado (mm)" else "No se pudo exportar", Toast.LENGTH_LONG).show()
        }
    }

    fun exportNow() {
        if (allSolids.isEmpty()) {
            Toast.makeText(context, "No hay piezas para exportar", Toast.LENGTH_SHORT).show()
        } else exporter.launch("${project.name}.stl")
    }

    Column(Modifier.fillMaxSize().background(CadColors.Ink)) {

        // ---------- Cabecera ----------
        Row(
            Modifier.fillMaxWidth().background(CadColors.Panel).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Home, contentDescription = "Proyectos", tint = CadColors.GoldMain, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name, color = CadColors.Text, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "NOCTURNE STUDIO 3D", color = CadColors.GoldMain.copy(alpha = 0.8f),
                    fontSize = 10.sp, letterSpacing = 2.sp
                )
            }
            IconButton(onClick = { helpOpen = true }) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(CadColors.GoldMain),
                    contentAlignment = Alignment.Center
                ) { Text("?", color = CadColors.OnGold, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Text("⋮", color = CadColors.Text, fontSize = 24.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Guardar proyecto") }, onClick = { menuOpen = false; saveNow() })
                    DropdownMenuItem(text = { Text("Exportar STL") }, onClick = { menuOpen = false; exportNow() })
                }
            }
        }

        // ---------- Barra de herramientas ----------
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(CadColors.PanelHi),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(
                IconKind.Undo,
                if (m is Mode.Sketching) sUndo.isNotEmpty() else doc.canUndo,
                { if (m is Mode.Sketching) sketchUndo() else doc.undo() },
                Modifier.weight(1f)
            )
            BarIcon(
                IconKind.Redo,
                if (m is Mode.Sketching) sRedo.isNotEmpty() else doc.canRedo,
                { if (m is Mode.Sketching) sketchRedo() else doc.redo() },
                Modifier.weight(1f)
            )
            BarDivider()
            BarIcon(
                IconKind.Pencil, m !is Mode.Extruding,
                {
                    if (m is Mode.Idle) {
                        val sf = selFace
                        startSketch(sf?.plane ?: selPlane ?: lastPlane)
                    }
                    else if (m is Mode.Sketching) catalogOpen = !catalogOpen
                },
                Modifier.weight(1f), accent = m is Mode.Sketching
            )
            BarDivider()
            Box(Modifier.weight(1f)) {
                BarIcon(IconKind.Dots, true, { moreOpen = true }, Modifier.fillMaxWidth())
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(text = { Text("Vista isométrica") }, onClick = { moreOpen = false; camera.setView(-35f, 25f) })
                    DropdownMenuItem(text = { Text("Ajustar a pantalla") }, onClick = { moreOpen = false; isoFit(max(extent, 10f)) })
                }
            }
            BarIcon(
                IconKind.Extrude, m is Mode.Idle, { if (m is Mode.Idle) startExtrude() },
                Modifier.weight(1f), accent = m is Mode.Extruding
            )
            BarDivider()
            BarIcon(IconKind.Clock, true, { panelOpen = !panelOpen }, Modifier.weight(1f), accent = panelOpen)
            BarIcon(IconKind.Balance, true, { propsOpen = true }, Modifier.weight(1f))
        }

        // ---------- Lienzo ----------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Viewport(
                camera = camera,
                visible = visible,
                highlight = sk?.plane ?: selPlane,
                planeSize = planeSize,
                gridHalf = gridHalf,
                solids = solids,
                polys = polysOther,
                activePolys = activePolys,
                previewPolys = previewPolys,
                labels = labels,
                dims = dims,
                cursor = cursor3,
                sketchPlane = sk?.plane,
                sketchInput = if (sk != null && tool != null) {
                    { ph: Int, u: Float, v: Float -> onSketchInput(ph, u, v) }
                } else null,
                sketchOrigin = if (sk != null) planeDef(sk.plane).o else V3(0f, 0f, 0f),
                quadHalf = quadHalf,
                sketchTitle = sk?.name,
                touchOffsetDp = if (offsetCursor && tool != SketchTool.Dimension) 72f else 0f,
                cursorSnap = cursorSnap,
                shadeMode = shade,
                guideH = guideH3,
                guideV = guideV3,
                snapLabel = snapLabel,
                selectedFace = selFace?.let { it.solidName to it.faceIndex },
                onTap = if (m is Mode.Idle) {
                    { pos: Offset ->
                        val pk = pickFace(camera, solids, pos.x, pos.y)
                        if (pk != null) {
                            val old = selFace
                            selFace = if (old != null && pk.solidName == old.solidName &&
                                pk.faceIndex == old.faceIndex
                            ) null else pk
                            selPlane = null
                        } else {
                            val pl = pickPlaneAt(camera, planeSize, visible, pos.x, pos.y)
                            selPlane = if (pl != null && pl != selPlane) pl else null
                            selFace = null
                        }
                    }
                } else if (m is Mode.Sketching && tool == null) {
                    { pos: Offset ->
                        val d = planeDef(m.draft.plane)
                        val (tu, tv) = camera.toPlane(
                            pos.x, pos.y, camera.viewSize.width / 2f, camera.viewSize.height / 2f, d.o
                        )
                        val hit = hitDim(m.draft.shapes, Pt(tu, tv), camera.zoom, false)
                        if (hit != null) {
                            moveStartLine = false
                            catalogOpen = false
                            dimTarget = hit
                        }
                    }
                } else if (m is Mode.Extruding && (m.draft.endType == EndType.ToPart ||
                        m.draft.endType == EndType.ToFace || m.draft.endType == EndType.ToVertex)
                ) {
                    { pos: Offset ->
                        val d = m.draft
                        when (d.endType) {
                            EndType.ToFace -> {
                                val pk = pickFace(camera, solids, pos.x, pos.y)
                                if (pk != null) mode = m.copy(draft = d.copy(endRef = pk.plane))
                            }
                            EndType.ToPart -> {
                                val nm = pickBody(camera, solids, pos.x, pos.y)
                                if (nm != null) mode = m.copy(draft = d.copy(endRef = nm))
                            }
                            EndType.ToVertex -> {
                                val vk = pickVertex(camera, solids, pos.x, pos.y)
                                if (vk != null) mode = m.copy(draft = d.copy(endRef = vk))
                            }
                            else -> {}
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxSize()
            )

            // Anillo con candado + cubo de vistas
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(CadColors.Panel.copy(alpha = 0.85f))
                        .border(1.5.dp, if (camera.locked) CadColors.GoldMain else CadColors.GoldSoft, CircleShape)
                        .clickable { camera.locked = !camera.locked },
                    contentAlignment = Alignment.Center
                ) {
                    CadIcon(if (camera.locked) IconKind.Lock else IconKind.Unlock, CadColors.GoldMain, 26.dp)
                }
                Box {
                    ViewCube(camera, onClick = { viewMenu = true })
                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                        fun go(y: Float, p: Float) { viewMenu = false; camera.setView(y, p) }
                        DropdownMenuItem(text = { Text("Planta") }, onClick = { go(0f, 90f) })
                        DropdownMenuItem(text = { Text("Inferior") }, onClick = { go(180f, -90f) })
                        DropdownMenuItem(text = { Text("Alzado") }, onClick = { go(0f, 0f) })
                        DropdownMenuItem(text = { Text("Atrás") }, onClick = { go(180f, 0f) })
                        DropdownMenuItem(text = { Text("Derecha") }, onClick = { go(-90f, 0f) })
                        DropdownMenuItem(text = { Text("Izquierda") }, onClick = { go(90f, 0f) })
                        DropdownMenuItem(text = { Text("Isométrica") }, onClick = { go(-45f, 35.264f) })
                        DropdownMenuItem(text = { Text("Dimétrico") }, onClick = { go(-25f, 20f) })
                        DropdownMenuItem(text = { Text("Trimétrica") }, onClick = { go(-35f, 25f) })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (camera.perspective) "Desactivar perspectiva" else "Activar perspectiva") },
                            onClick = { viewMenu = false; camera.perspective = !camera.perspective }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text((if (shade == ShadeMode.Shaded) "✓ " else "") + "Sombreado") },
                            onClick = { viewMenu = false; shade = ShadeMode.Shaded }
                        )
                        DropdownMenuItem(
                            text = { Text((if (shade == ShadeMode.NoEdges) "✓ " else "") + "Sombreado sin aristas") },
                            onClick = { viewMenu = false; shade = ShadeMode.NoEdges }
                        )
                        DropdownMenuItem(
                            text = { Text((if (shade == ShadeMode.HiddenEdges) "✓ " else "") + "Sombreado con aristas ocultas") },
                            onClick = { viewMenu = false; shade = ShadeMode.HiddenEdges }
                        )
                        DropdownMenuItem(
                            text = { Text((if (shade == ShadeMode.Wire) "✓ " else "") + "Alámbrico") },
                            onClick = { viewMenu = false; shade = ShadeMode.Wire }
                        )
                    }
                }
            }

            if (m is Mode.Idle) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(12.dp).size(54.dp).clip(CircleShape)
                        .background(CadColors.GoldMain)
                        .clickable { panelOpen = !panelOpen },
                    contentAlignment = Alignment.Center
                ) { CadIcon(IconKind.Tree, CadColors.OnGold, 28.dp) }

                val dims = sizeOf(solids)
                if (dims != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 78.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = CadColors.Panel
                    ) {
                        Text(
                            "${fmt(dims.x)} × ${fmt(dims.y)} × ${fmt(dims.z)} cm",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = CadColors.GoldMain, fontSize = 13.sp
                        )
                    }
                }
            }

            if (drawing && cur != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp, end = 130.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = CadColors.Panel.copy(alpha = 0.92f)
                ) {
                    Text(
                        readout(tool, pending, cur),
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = CadColors.GoldMain, fontSize = 13.sp, lineHeight = 16.sp
                    )
                }
            }

            val sf = selFace
            val spl = selPlane
            if (m is Mode.Idle && (sf != null || spl != null)) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp, start = 76.dp, end = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = CadColors.Panel
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (sf != null) "Cara seleccionada · usa el lápiz para dibujar sobre ella"
                            else "Plano seleccionado: ${planeLabel(spl ?: "")} · usa el lápiz para dibujar",
                            color = CadColors.GoldMain, fontSize = 12.sp, lineHeight = 14.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable { selFace = null; selPlane = null },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", color = CadColors.Text, fontSize = 16.sp) }
                    }
                }
            }

            if (panelOpen) {
                FeatureTree(
                    ops = doc.ops,
                    parts = allSolids,
                    visible = visible,
                    hidden = hidden,
                    onEdit = { editOp(it) },
                    onDelete = { doc.delete(it.id) },
                    onClose = { panelOpen = false },
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp, bottom = 78.dp)
                        .width(300.dp).fillMaxHeight()
                )
            }

            when (m) {
                is Mode.Sketching -> SketchPanel(
                    draft = m.draft,
                    isNew = m.isNew,
                    toolName = tool?.label,
                    hint = hintFor(tool, pending.size),
                    canFinish = pending.isNotEmpty(),
                    facePlane = selFace?.plane,
                    onPlaneChange = { p ->
                        mode = m.copy(draft = m.draft.copy(plane = p))
                        if (p in PLANES) lastPlane = p
                        viewFor(p)
                    },
                    offsetOn = offsetCursor,
                    onToggleOffset = { offsetCursor = !offsetCursor },
                    onTools = { catalogOpen = true },
                    onUndoShape = {
                        setShapes(m, m.draft.shapes.dropLast(1))
                        pending = emptyList()
                        chainStart = null
                    },
                    onFinish = { pending = emptyList(); cursor = null; chainStart = null },
                    onConfirm = {
                        doc.upsert(m.draft)
                        endSketchSession()
                        mode = Mode.Idle
                        isoFit(extent)
                    },
                    onCancel = { endSketchSession(); mode = Mode.Idle; isoFit(extent) },
                    onDelete = { doc.delete(m.draft.id); endSketchSession(); mode = Mode.Idle; isoFit(extent) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                is Mode.Extruding -> {
                    val extSketch = doc.ops.firstOrNull { it.id == m.draft.sketchId } as? SketchOp
                    ExtrudePanel(
                        draft = m.draft,
                        sketchName = extSketch?.name ?: "",
                        regionCount = extSketch?.let { profiles(it.shapes).size } ?: 1,
                        isNew = m.isNew,
                        onChange = { mode = m.copy(draft = it) },
                        onEdit = { key -> extCalc = key },
                        onSoon = { label ->
                            Toast.makeText(context, "$label: próximamente", Toast.LENGTH_SHORT).show()
                        },
                        onConfirm = { extCalc = null; doc.upsert(m.draft); mode = Mode.Idle; isoFit(extent) },
                        onCancel = { extCalc = null; mode = Mode.Idle; isoFit(extent) },
                        onDelete = { extCalc = null; doc.delete(m.draft.id); mode = Mode.Idle; isoFit(extent) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                Mode.Idle -> {}
            }

            // Calculadora de la extrusión (profundidad, espesor, desfase, ángulo...)
            val ek = extCalc
            if (m is Mode.Extruding && ek != null) {
                val d = m.draft
                val isAngle = ek == "draft"
                val curE = when (ek) {
                    "depth" -> d.depth
                    "thick" -> d.thickness
                    "offset" -> d.startOffset
                    "second" -> d.second
                    else -> d.draftDeg
                }
                DimCalculator(
                    initial = fmt(if (isAngle) curE else curE / unit.toCm),
                    unit = unit,
                    onUnit = { unit = it },
                    isFixed = false,
                    onFree = {},
                    showMove = false,
                    moveStart = false,
                    onToggleMove = {},
                    onDelete = {},
                    onCancel = { extCalc = null },
                    onAccept = { v ->
                        mode = m.copy(
                            draft = when (ek) {
                                "depth" -> d.copy(depth = v)
                                "thick" -> d.copy(thickness = v)
                                "offset" -> d.copy(startOffset = v)
                                "second" -> d.copy(second = v)
                                else -> d.copy(draftDeg = v.coerceIn(0f, 60f))
                            }
                        )
                        extCalc = null
                    },
                    fixedUnit = if (isAngle) "°" else null,
                    showDelete = false,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }

            // Calculadora de cotas
            val cd = dimTarget
            if (m is Mode.Sketching && cd != null && cd.index in m.draft.shapes.indices) {
                val shp = m.draft.shapes[cd.index]
                val curV = dimValue(shp, cd.which)
                if (curV != null) {
                    DimCalculator(
                        initial = fmt(curV / unit.toCm),
                        unit = unit,
                        onUnit = { unit = it },
                        isFixed = isDimFixed(shp, cd.which),
                        onFree = {
                            val list: List<Shape> = m.draft.shapes.toMutableList().also {
                                it[cd.index] = setFixed(shp, cd.which, false)
                            }
                            setShapes(m, list)
                            dimTarget = null
                        },
                        showMove = shp is LineS,
                        moveStart = moveStartLine,
                        onToggleMove = { moveStartLine = !moveStartLine },
                        onDelete = {
                            setShapes(m, m.draft.shapes.filterIndexed { i, _ -> i != cd.index })
                            dimTarget = null
                        },
                        onCancel = { dimTarget = null },
                        onAccept = { v ->
                            val ns = withDim(shp, cd.which, v, moveStartLine)
                            if (ns != null) {
                                var list: List<Shape> = m.draft.shapes.toMutableList().also { it[cd.index] = ns }
                                if (shp is LineS && ns is LineS) {
                                    if (dist(shp.a, ns.a) > 1e-6f) list = moveCoincident(list, cd.index, shp.a, ns.a)
                                    if (dist(shp.b, ns.b) > 1e-6f) list = moveCoincident(list, cd.index, shp.b, ns.b)
                                }
                                if (violatesFixed(m.draft.shapes, list, cd.index)) {
                                    Toast.makeText(
                                        context,
                                        "Una línea conectada tiene su cota fija. Libérala primero.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    setShapes(m, list)
                                    dimTarget = null
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    )
                }
            }

            // Catálogo de herramientas de dibujo
            if (catalogOpen && m is Mode.Sketching) {
                ToolCatalog(
                    active = tool,
                    onPick = { t -> tool = t; pending = emptyList(); cursor = null; chainStart = null; catalogOpen = false },
                    onSoon = { label ->
                        Toast.makeText(context, "$label: próximamente", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { catalogOpen = false },
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                )
            }
        }
    }

    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text("Ayuda rápida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Un dedo: orbitar. Dos dedos: zoom y desplazar.")
                    Text("• Toca un plano o una cara para elegirlo y luego el lápiz. El plano también se cambia en el panel de abajo.")
                    Text("• Toca el cubo de vistas para ver la lista de vistas, perspectiva y sombreado.")
                    Text("• Toca una cara de una pieza y usa el lápiz para dibujar sobre ella.")
                    Text("• Dibujar: toca o arrastra y suelta para colocar cada punto.")
                    Text("• Cota: toca un elemento para cambiar su medida o borrarlo.")
                    Text("• Cubo con marco: extruye el último boceto cerrado.")
                    Text("• Reloj o círculo dorado: árbol de operaciones y piezas.")
                    Text("• Balanza: volumen y área de las piezas.")
                    Text("• ⋮: guardar y exportar STL.")
                }
            },
            confirmButton = { TextButton(onClick = { helpOpen = false }) { Text("Entendido") } }
        )
    }

    if (propsOpen) {
        AlertDialog(
            onDismissRequest = { propsOpen = false },
            title = { Text("Propiedades") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (allSolids.isEmpty()) {
                        Text("Aún no hay piezas. Dibuja un perfil cerrado y extrúyelo.")
                    } else {
                        allSolids.forEach { sd ->
                            Text(sd.name, fontWeight = FontWeight.Bold)
                            Text("Volumen: ${fmt(volumeOf(sd))} cm³")
                            Text("Área: ${fmt(areaOf(sd))} cm²")
                        }
                        Text(
                            "Volumen total: ${fmt(allSolids.sumOf { volumeOf(it).toDouble() }.toFloat())} cm³",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { propsOpen = false }) { Text("Cerrar") } }
        )
    }
}
