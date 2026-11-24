package com.modelo3d.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.modelo3d.studio.model.Project
import com.modelo3d.studio.model.ProjectStore
import com.modelo3d.studio.ui.CadDark
import com.modelo3d.studio.ui.EditorScreen
import com.modelo3d.studio.ui.ProjectListScreen
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProjectStore(File(filesDir, "projects"))
        setContent {
            MaterialTheme(colorScheme = CadDark) {
                Surface(Modifier.fillMaxSize()) {
                    var open by remember { mutableStateOf<Project?>(null) }
                    BackHandler(enabled = open != null) { open = null }
                    val current = open
                    if (current == null) ProjectListScreen(store) { open = it }
                    else EditorScreen(current, store) { open = null }
                }
            }
        }
    }
}
