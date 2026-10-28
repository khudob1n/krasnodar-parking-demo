package ru.krasnodar.parking.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.krasnodar.parking.shared.format.ParkingFormat
import ru.krasnodar.parking.shared.model.Parking
import ru.krasnodar.parking.ui.theme.Spacing

/** Список без теней и плашек: строки разделены тонкими линиями. */
@Composable
fun ParkingList(items: List<Parking>, query: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Column(modifier.padding(Spacing.l)) {
            Text(if (query.isBlank()) "Парковок нет" else "Ничего не нашлось", style = MaterialTheme.typography.titleLarge)
            Text(
                if (query.isBlank()) "Каталог пуст." else "Попробуйте улицу без «ул.» или номер парковки, например «Красная» или «1102».",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = Spacing.l)) {
        items(items, key = { it.id }) { p ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(p.id) }
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            ) {
                Text(ParkingFormat.number(p), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(p.address, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ParkingFormat.summary(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = Spacing.l), color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Скелетон списка на время первого чтения каталога. */
@Composable
fun ParkingListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = Spacing.l)) {
        repeat(6) {
            Spacer(Modifier.height(Spacing.m))
            Box(Modifier.fillMaxWidth(0.3f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.9f).height(18.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(Spacing.m))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}
