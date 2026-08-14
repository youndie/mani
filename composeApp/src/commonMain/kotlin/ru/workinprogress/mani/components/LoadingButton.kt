package ru.workinprogress.mani.components

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * [icon] — необязательный значок перед надписью; на время загрузки он уступает место
 * индикатору, иначе кнопка сообщала бы «нажми» и «жду» одновременно.
 */
@Composable
fun LoadingButton(
    modifier: Modifier = Modifier,
    loading: Boolean,
    enabled: Boolean = true,
    buttonText: String,
    icon: ImageVector? = null,
    onButtonClicked: () -> Unit,
) {
    Button(
        {
            onButtonClicked()
        },
        enabled = !loading && enabled,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.widthIn(min = 64.dp),
            horizontalArrangement = spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                icon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(buttonText)
            }
        }
    }
}
