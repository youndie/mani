package ru.workinprogress.feature.transaction.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.workinprogress.mani.theme.LocalManiFonts

/**
 * Первый запуск: правил ещё нет.
 *
 * Раньше здесь стояло «nothing to show ¯\\_(-_-)_/¯» — шутка на месте единственного экрана, где
 * человеку надо объяснить, чем это приложение отличается от трекера расходов. Теперь объясняем:
 * mani хранит правила, а не чеки.
 *
 * [onFillWithDemoData] может отсутствовать — в истории предлагать засев незачем.
 */
@Composable
fun TrasactionsEmpty(
    onAddFirstRule: (() -> Unit)? = null,
    onFillWithDemoData: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).testTag("transactionsEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Add what comes in and what goes out",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )

        Text(
            "mani keeps rules, not receipts. Enter a salary and a rent once, " +
                "and it works out the day the money ends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp).padding(bottom = 12.dp),
        )

        onAddFirstRule?.let {
            Button(onClick = it, modifier = Modifier.testTag("addFirstRule")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Add your first rule")
            }
        }

        onFillWithDemoData?.let {
            TextButton(onClick = it, modifier = Modifier.testTag("fillWithDemoData")) {
                Text("Fill it with demo data")
            }

            Text(
                "a salary, a rent, three subscriptions and one laptop",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalManiFonts.current.mono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
