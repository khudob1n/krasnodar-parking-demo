package ru.krasnodar.parking.ui.garage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.krasnodar.parking.ui.theme.Spacing

/** Гараж нескольких автомобилей — этап II. Модель автомобиля уже есть в shared. */
@Composable
fun GarageScreen() {
    Column(Modifier.fillMaxSize().padding(Spacing.l)) {
        Text("Гараж", style = MaterialTheme.typography.headlineLarge)
        Text("Пока пусто", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.l))
        Text(
            "Добавление нескольких автомобилей по госномеру с ручным вводом появится на следующем этапе. Номера хранятся только на этом устройстве.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
