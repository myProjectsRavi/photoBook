package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photobook.app.R

@Composable
fun OnboardingScreen(
    isIndexing: Boolean,
    progress: Float,
    onGrantPermission: () -> Unit,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )

        if (isIndexing) {
            Text(
                text = stringResource(R.string.indexing_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )
        } else {
            val hasRecoverableError = !errorMessage.isNullOrBlank()
            Text(
                text = errorMessage ?: stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Button(
                onClick = if (hasRecoverableError) onRetry else onGrantPermission,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(
                    text = if (hasRecoverableError) {
                        stringResource(R.string.retry)
                    } else {
                        stringResource(R.string.allow_photo_access)
                    },
                )
            }
        }
    }
}
