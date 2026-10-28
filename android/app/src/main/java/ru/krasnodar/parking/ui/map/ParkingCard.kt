package ru.krasnodar.parking.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.krasnodar.parking.shared.format.ParkingFormat
import ru.krasnodar.parking.shared.model.CatalogSource
import ru.krasnodar.parking.shared.model.Parking
import ru.krasnodar.parking.ui.theme.Spacing

/**
 * Карточка выбранной парковки. Порядок из спецификации: номер → адрес →
 * тариф → свободные места → часы → «Припарковаться».
 */
@Composable
fun ParkingCard(parking: Parking, source: CatalogSource?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ParkingFormat.kind(parking), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ParkingFormat.number(parking), style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Закрыть карточку") }
            }
            Text(
                "Номер из данных портала. Сверьте его с табличкой на месте.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(parking.address, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.m))
            if (parking.description.isNotBlank()) {
                Text(parking.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                InfoRow("Тариф", ParkingFormat.price(parking), emphasize = true)
                InfoRow("Свободные места", ParkingFormat.freePlaces(parking))
                InfoRow("Платное время", ParkingFormat.paidHours(parking))
                InfoRow("Вместимость", ParkingFormat.capacity(parking))
                InfoRow("Тарифная зона", ParkingFormat.tariffZone(parking))
            }
            if (parking.tariff.text.isNotBlank()) {
                Text(
                    "Как в источнике: " + parking.tariff.text.replace("\n", "; "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
            IssueNotes(parking)
            Text(
                ParkingFormat.sourceLine(source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m),
            )

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .padding(top = Spacing.l)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Припарковаться") }
            Text(
                "Сценарий парковки появится в следующей версии демо. Оплата в демо не проводится.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.s),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, emphasize: Boolean = false) {
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                value,
                style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
                color = if (value == ParkingFormat.NO_DATA) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun IssueNotes(p: Parking) {
    val notes = buildList {
        if ("location_out_of_bounds" in p.issues) add("В источнике ошибочные координаты: парковка не показана на карте.")
        if ("tariff_text_mismatch" in p.issues) add("Текст тарифа в источнике расходится с полями часов или цены.")
        if ("paid_with_zero_price" in p.issues) add("Платная парковка с ценой 0 в источнике: тариф уточняйте на месте.")
        if (p.segments.isEmpty() && p.location != null) add("Линий участка в источнике нет, на карте показана точка.")
    }
    for (n in notes) {
        Text(n, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = Spacing.s))
    }
}
