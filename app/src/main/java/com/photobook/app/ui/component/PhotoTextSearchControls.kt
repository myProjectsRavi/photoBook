package com.photobook.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.photobook.app.R
import com.photobook.app.feature.phototextsearch.PhotoTextCompleteness
import com.photobook.app.feature.phototextsearch.PhotoTextSearchController
import com.photobook.app.feature.phototextsearch.PhotoTextSearchPhase
import com.photobook.app.feature.phototextsearch.PhotoTextSearchState

@Composable
fun PhotoTextSearchControls(
    state: PhotoTextSearchState,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowMatch: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xE61A1A1A),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text(stringResource(R.string.viewer_search_text_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.viewer_search_clear),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() },
                ),
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.viewer_search_character_count,
                            state.query.length,
                            PhotoTextSearchController.MAX_QUERY_CHARACTERS,
                        ),
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = statusText(state),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )

                if (state.matches.isNotEmpty()) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.viewer_search_previous),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.viewer_search_next),
                            tint = Color.White,
                        )
                    }
                    TextButton(onClick = onShowMatch) {
                        Text(stringResource(R.string.viewer_search_show_match))
                    }
                } else if (
                    state.phase == PhotoTextSearchPhase.FAILED ||
                    (
                        state.phase == PhotoTextSearchPhase.READY &&
                            state.layout?.elements.isNullOrEmpty()
                    )
                ) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.viewer_search_read_again))
                    }
                }

                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.viewer_close))
                }
            }
        }
    }
}

@Composable
private fun statusText(state: PhotoTextSearchState): String {
    return when (state.phase) {
        PhotoTextSearchPhase.CLOSED -> ""
        PhotoTextSearchPhase.PREPARING -> {
            if (state.isSlow) {
                stringResource(R.string.viewer_search_still_reading)
            } else {
                stringResource(R.string.viewer_search_reading)
            }
        }
        PhotoTextSearchPhase.UNAVAILABLE -> {
            stringResource(R.string.viewer_search_unavailable)
        }
        PhotoTextSearchPhase.FAILED -> {
            stringResource(R.string.viewer_search_failed)
        }
        PhotoTextSearchPhase.READY -> {
            when {
                state.queryTooLong -> stringResource(
                    R.string.viewer_search_query_too_long,
                    PhotoTextSearchController.MAX_QUERY_CHARACTERS,
                )
                state.layout?.elements.isNullOrEmpty() -> {
                    stringResource(R.string.viewer_search_no_text)
                }
                state.query.isBlank() -> {
                    stringResource(R.string.viewer_search_type_prompt)
                }
                state.matches.isNotEmpty() -> {
                    stringResource(
                        R.string.viewer_search_match_count,
                        state.activeMatchIndex + 1,
                        state.matches.size,
                    )
                }
                state.layout?.completeness == PhotoTextCompleteness.PARTIAL -> {
                    stringResource(R.string.viewer_search_partial)
                }
                else -> stringResource(R.string.viewer_search_no_match)
            }
        }
    }
}
