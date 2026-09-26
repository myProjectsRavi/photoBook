package com.photobook.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.photobook.app.R
import com.photobook.app.feature.phototextsearch.PhotoTextCompleteness
import com.photobook.app.feature.phototextsearch.PhotoTextSearchController
import com.photobook.app.feature.phototextsearch.PhotoTextSearchPhase
import com.photobook.app.feature.phototextsearch.PhotoTextSearchState

@Composable
fun PhotoTextSearchHeader(
    state: PhotoTextSearchState,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowMatch: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    showMatchAction: Boolean,
    compactNavigation: Boolean,
    requestInitialFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SEARCH_CHROME_COLOR,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SEARCH_HEADER_MIN_HEIGHT)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onClose()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.viewer_close),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            SearchQueryField(
                state = state,
                onQueryChange = onQueryChange,
                onClear = {
                    onQueryChange("")
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                focusRequester = focusRequester,
                onImeSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                modifier = Modifier.weight(1f),
            )

            if (compactNavigation) {
                PhotoTextSearchNavigationContent(
                    state = state,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onShowMatch = onShowMatch,
                    onRetry = onRetry,
                    showMatchAction = showMatchAction,
                    compact = true,
                    modifier = Modifier
                        .widthIn(min = 132.dp, max = 260.dp)
                        .semantics { testTagsAsResourceId = true }
                        .testTag(PHOTO_TEXT_SEARCH_COMPACT_NAV_TEST_TAG),
                )
            }
        }
    }
}

@Composable
fun PhotoTextSearchNavigation(
    state: PhotoTextSearchState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowMatch: () -> Unit,
    onRetry: () -> Unit,
    showMatchAction: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SEARCH_CHROME_COLOR,
    ) {
        PhotoTextSearchNavigationContent(
            state = state,
            onPrevious = onPrevious,
            onNext = onNext,
            onShowMatch = onShowMatch,
            onRetry = onRetry,
            showMatchAction = showMatchAction,
            compact = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun PhotoTextSearchBackHandler(
    enabled: Boolean,
    imeVisible: Boolean,
    onClose: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = enabled) {
        if (imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onClose()
        }
    }
}

@Composable
private fun SearchQueryField(
    state: PhotoTextSearchState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    onImeSearch: () -> Unit,
    modifier: Modifier,
) {
    TextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = modifier
            .heightIn(min = 56.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.viewer_search_text_hint),
                color = SEARCH_SECONDARY_COLOR,
            )
        },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.viewer_search_clear),
                        tint = Color.White,
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onImeSearch() },
        ),
        isError = state.queryTooLong,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            errorTextColor = Color.White,
            focusedContainerColor = SEARCH_FIELD_COLOR,
            unfocusedContainerColor = SEARCH_FIELD_COLOR,
            errorContainerColor = SEARCH_FIELD_COLOR,
            cursorColor = Color.White,
            errorCursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTrailingIconColor = Color.White,
            unfocusedTrailingIconColor = Color.White,
            errorTrailingIconColor = Color.White,
            focusedPlaceholderColor = SEARCH_SECONDARY_COLOR,
            unfocusedPlaceholderColor = SEARCH_SECONDARY_COLOR,
            errorPlaceholderColor = SEARCH_SECONDARY_COLOR,
        ),
    )
}

@Composable
private fun PhotoTextSearchNavigationContent(
    state: PhotoTextSearchState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowMatch: () -> Unit,
    onRetry: () -> Unit,
    showMatchAction: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val statusColor = if (
        state.queryTooLong ||
        state.phase == PhotoTextSearchPhase.FAILED ||
        state.phase == PhotoTextSearchPhase.UNAVAILABLE
    ) {
        SEARCH_ERROR_COLOR
    } else {
        SEARCH_SECONDARY_COLOR
    }
    val retryLabel = when {
        state.phase == PhotoTextSearchPhase.FAILED -> stringResource(R.string.viewer_search_retry)
        state.phase == PhotoTextSearchPhase.READY && state.layout?.elements.isNullOrEmpty() ->
            stringResource(R.string.viewer_search_read_again)
        else -> null
    }

    if (compact) {
        Row(
            modifier = modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = statusText(state),
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            MatchNavigationButtons(
                state = state,
                onPrevious = onPrevious,
                onNext = onNext,
            )
            when {
                showMatchAction -> {
                    TextButton(
                        onClick = onShowMatch,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.viewer_search_show_match),
                            color = Color.White,
                        )
                    }
                }
                retryLabel != null -> {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(text = retryLabel, color = Color.White)
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier.heightIn(min = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = statusText(state),
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (shouldShowCharacterCount(state)) {
                    Text(
                        text = stringResource(
                            R.string.viewer_search_character_count,
                            state.query.length,
                            PhotoTextSearchController.MAX_QUERY_CHARACTERS,
                        ),
                        color = if (state.queryTooLong) SEARCH_ERROR_COLOR else SEARCH_SECONDARY_COLOR,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            MatchNavigationButtons(
                state = state,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }


internal const val PHOTO_TEXT_SEARCH_VIEWPORT_TEST_TAG = "photo_text_search_viewport"
internal const val PHOTO_TEXT_SEARCH_COMPACT_NAV_TEST_TAG = "photo_text_search_compact_navigation"
