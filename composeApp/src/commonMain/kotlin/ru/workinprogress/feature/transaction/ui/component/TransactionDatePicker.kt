package ru.workinprogress.feature.transaction.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import ru.workinprogress.mani.theme.LocalManiFonts

/**
 * Поле даты: подпись стоит **над** полем (её ставит вызывающий), внутри — только сама дата.
 *
 * Плавающая метка Material занимала строку внутри рамки и делала два узких поля в ряд нечитаемыми;
 * в макете подпись вынесена наружу, а место внутри отдано дате и значку календаря.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDatePicker(
    modifier: Modifier = Modifier,
    value: String?,
    placeholder: String? = null,
    datePickerState: DatePickerState,
    showDialog: Boolean,
    onToggleDatePicker: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = {
        },
        singleLine = true,
        readOnly = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalManiFonts.current.mono),
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalManiFonts.current.mono),
                )
            }
        },
        trailingIcon = {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.fillMaxWidth(),
        interactionSource = remember { MutableInteractionSource() }
            .also { interactionSource ->
                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collect {
                        if (it is PressInteraction.Release) {
                            onToggleDatePicker()
                        }
                    }
                }
            },
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = onToggleDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis
                        ?.toDate
                        ?.let(onDateSelected)
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onToggleDatePicker) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
