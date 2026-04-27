package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photobook.app.R
import com.photobook.app.feature.vault.VaultItem
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultBottomSheet(
    items: List<VaultItem>,
    isLoading: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSaveToDevice: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.vault_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = !isLoading && !isBusy,
                ) {
                    Text(text = stringResource(R.string.vault_refresh))
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(text = stringResource(R.string.vault_loading))
                }
            } else if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.vault_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = items,
                        key = { item -> item.id },
                    ) { item ->
                        VaultItemCard(
                            item = item,
                            isBusy = isBusy,
                            onSaveToDevice = { onSaveToDevice(item) },
                            onDelete = { onDelete(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    isBusy: Boolean,
    onSaveToDevice: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.originalFileName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.vault_item_meta,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.addedAtMs)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSaveToDevice,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.vault_save_to_device))
                }
                TextButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                ) {
                    Text(text = stringResource(R.string.vault_delete))
                }
            }
        }
    }
}
