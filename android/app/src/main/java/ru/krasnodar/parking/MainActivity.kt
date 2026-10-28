package ru.krasnodar.parking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.krasnodar.parking.ui.garage.GarageScreen
import ru.krasnodar.parking.ui.map.MapScreen
import ru.krasnodar.parking.ui.profile.ProfileScreen
import ru.krasnodar.parking.ui.theme.KrasnodarParkingTheme

private enum class Tab(val title: String, val icon: ImageVector) {
    MAP("Карта", Icons.Outlined.Map),
    GARAGE("Гараж", Icons.Outlined.DirectionsCar),
    PROFILE("Профиль", Icons.Outlined.Person),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KrasnodarParkingTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.MAP) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Плоская навигация: тонкая граница сверху, без стекла и теней.
            Box {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(250), modifier = Modifier.padding(padding).fillMaxSize(), label = "tab") { t ->
            when (t) {
                Tab.MAP -> MapScreen()
                Tab.GARAGE -> GarageScreen()
                Tab.PROFILE -> ProfileScreen()
            }
        }
    }
}
