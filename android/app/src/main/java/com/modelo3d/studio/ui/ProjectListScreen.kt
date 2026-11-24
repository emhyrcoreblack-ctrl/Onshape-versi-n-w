package com.modelo3d.studio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelo3d.studio.model.Project
import com.modelo3d.studio.model.ProjectStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(store: ProjectStore, onOpen: (Project) -> Unit) {
    var projects by remember { mutableStateOf(store.list()) }
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    "NOCTURNE STUDIO 3D", color = CadColors.GoldMain,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp
                )
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { name = ""; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo proyecto")
            }
        }
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
                Text("Aún no tienes proyectos. Toca + para crear el primero.")
            }
        }
        LazyColumn(Modifier.padding(pad)) {
            items(projects, key = { it.id }) { p ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(p) },
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        Text("Modificado: " + DateFormat.getDateTimeInstance().format(Date(p.modified)))
                    },
                    trailingContent = {
                        IconButton(onClick = { store.delete(p); projects = store.list() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nuevo proyecto") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nombre") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = store.create(name.ifBlank { "Proyecto sin título" })
                    projects = store.list(); showDialog = false; onOpen(p)
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }
}
