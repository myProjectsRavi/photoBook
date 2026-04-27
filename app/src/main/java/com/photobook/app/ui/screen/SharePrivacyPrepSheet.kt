package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photobook.app.R
import com.photobook.app.feature.metadata.SharePrivacySummary

sealed interface SharePrivacyPrepState {
    data object Loading : SharePrivacyPrepState
    data class Ready(val summary: SharePrivacySummary) : SharePrivacyPrepState
    data object Error : SharePrivacyPrepState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePrivacyPrepSheet(
    state: SharePrivacyPrepState,
    stripMetadata: Boolean,
    blurFaces: Boolean,
    isPreparingShare: Boolean,
    onToggleStripMetadata: (Boolean) -> Unit,
    onToggleBlurFaces: (Boolean) -> Unit,
    onRetryScan: () -> Unit,
    onConfirmShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.share_prep_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.share_prep_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state) {
                SharePrivacyPrepState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.share_prep_scanning),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                SharePrivacyPrepState.Error -> {
                    Text(
                        text = stringResource(R.string.share_prep_scan_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetryScan) {
                        Text(text = stringResource(R.string.viewer_copy_text_retry))
                    }
                }

                is SharePrivacyPrepState.Ready -> {
                    val summary = state.summary
                    Text(
                        text = stringResource(
                            R.string.share_prep_summary,
                            summary.photoCount,
                            summary.faceCount,
                            summary.metadataRiskCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ShareOptionRow(
                title = stringResource(R.string.share_prep_option_strip_metadata),
                subtitle = stringResource(R.string.share_prep_option_strip_metadata_subtitle),
                checked = stripMetadata,
                onCheckedChange = onToggleStripMetadata,
            )

            ShareOptionRow(
                title = stringResource(R.string.share_prep_option_blur_faces),
                subtitle = stringResource(R.string.share_prep_option_blur_faces_subtitle),
                checked = blurFaces,
                onCheckedChange = onToggleBlurFaces,
            )

            Button(
                onClick = onConfirmShare,
                enabled = state !is SharePrivacyPrepState.Loading && !isPreparingShare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPreparingShare) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(text = stringResource(R.string.share_prep_continue))
                }
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
