package io.jauge.feature.share.shares

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.jauge.core.designsystem.component.EmptyState
import io.jauge.core.designsystem.component.GaugeSize
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugeGauge
import io.jauge.core.designsystem.component.JaugeRefreshContainer
import io.jauge.core.designsystem.component.JaugeScreenScaffold
import io.jauge.core.designsystem.component.SkeletonList
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.ShareFormat
import io.jauge.core.ui.error.FriendlyErrorView
import io.jauge.core.ui.error.toFriendlyMessage
import io.jauge.feature.share.common.joinWithDots
import io.jauge.feature.share.platform.rememberNativeSharer
import io.jauge.feature.share.resources.Res
import io.jauge.feature.share.resources.months_short
import io.jauge.feature.share.resources.share_format_link
import io.jauge.feature.share.resources.share_format_square
import io.jauge.feature.share.resources.share_format_story
import io.jauge.feature.share.resources.share_message_link_copied
import io.jauge.feature.share.resources.shares_copy_link
import io.jauge.feature.share.resources.shares_delete
import io.jauge.feature.share.resources.shares_delete_cancel
import io.jauge.feature.share.resources.shares_delete_confirm
import io.jauge.feature.share.resources.shares_delete_message
import io.jauge.feature.share.resources.shares_delete_title
import io.jauge.feature.share.resources.shares_deleted
import io.jauge.feature.share.resources.shares_empty_message
import io.jauge.feature.share.resources.shares_empty_title
import io.jauge.feature.share.resources.shares_format_date
import io.jauge.feature.share.resources.shares_headline
import io.jauge.feature.share.resources.shares_overline
import io.jauge.feature.share.resources.shares_unknown_title
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every shared analysis with its public link (tab "Partages").
 *
 * @param onOpenAnalysis opens the verdict of a shared analysis.
 */
@Composable
fun SharesScreen(onOpenAnalysis: (String) -> Unit) {
    val viewModel: SharesViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sharer = rememberNativeSharer()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SharesEvent.Deleted -> getString(Res.string.shares_deleted)
                is SharesEvent.Failed -> getString(event.error.toFriendlyMessage().title)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    SharesLayout(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onOpenAnalysis = onOpenAnalysis,
        onCopyLink = { item ->
            sharer.copyText(item.share.url)
            coroutineScope.launch { snackbarHostState.showSnackbar(getString(Res.string.share_message_link_copied)) }
        },
        onDelete = viewModel::askToDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
    )
}

/**
 * Stateless layout of the tab "Partages", also used by screenshot tests.
 *
 * @param state observed state.
 * @param snackbarHostState host of the confirmations.
 * @param onRefresh reloads the shares.
 * @param onOpenAnalysis opens the verdict of a shared analysis.
 * @param onCopyLink copies the public link of a share.
 * @param onDelete asks to delete a share.
 * @param onConfirmDelete confirms the deletion.
 * @param onCancelDelete cancels the deletion.
 */
@Composable
fun SharesLayout(
    state: SharesUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onCopyLink: (ShareItem) -> Unit,
    onDelete: (ShareItem) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    val shares = state.shares.content
    JaugeScreenScaffold(showTopBar = false, snackbarHostState = snackbarHostState) {
        JaugeRefreshContainer(isRefreshing = state.shares.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = JaugeTheme.spacing.screenGutter, end = JaugeTheme.spacing.screenGutter, top = 12.dp, bottom = JaugeTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium),
            ) {
                item(key = "header") {
                    Column(Modifier.padding(bottom = 6.dp)) {
                        Text(
                            text = stringResource(Res.string.shares_overline).uppercase(),
                            style = JaugeTheme.typography.overline,
                            color = JaugeTheme.colors.textSecondary,
                        )
                        Text(
                            text = stringResource(Res.string.shares_headline),
                            style = JaugeTheme.typography.headline,
                            color = JaugeTheme.colors.textPrimary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                when {
                    shares != null && shares.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            title = stringResource(Res.string.shares_empty_title),
                            message = stringResource(Res.string.shares_empty_message),
                            icon = JaugeIcons.Share,
                        )
                    }
                    shares != null -> items(shares, key = { it.share.id }) { item ->
                        ShareRow(item, onOpenAnalysis, onCopyLink, onDelete)
                    }
                    state.shares.showsFullScreenError -> item(key = "error") {
                        FriendlyErrorView(error = state.shares.error!!, onAction = { onRefresh() })
                    }
                    else -> item(key = "loading") { SkeletonList(rowCount = 3) }
                }
            }
        }
    }
    state.shareToDelete?.let {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            containerColor = JaugeTheme.colors.surface,
            title = { Text(stringResource(Res.string.shares_delete_title), style = JaugeTheme.typography.sectionTitle) },
            text = { Text(stringResource(Res.string.shares_delete_message), style = JaugeTheme.typography.bodySmall, color = JaugeTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(Res.string.shares_delete_confirm), style = JaugeTheme.typography.label, color = JaugeTheme.colors.crowded.content)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) {
                    Text(stringResource(Res.string.shares_delete_cancel), style = JaugeTheme.typography.label, color = JaugeTheme.colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun ShareRow(
    item: ShareItem,
    onOpenAnalysis: (String) -> Unit,
    onCopyLink: (ShareItem) -> Unit,
    onDelete: (ShareItem) -> Unit,
) {
    val colors = JaugeTheme.colors
    val analysis = item.analysis
    val date = item.share.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val months = stringArrayResource(Res.array.months_short)
    val formatLabel = stringResource(
        when (item.share.options.format) {
            ShareFormat.STORY -> Res.string.share_format_story
            ShareFormat.SQUARE -> Res.string.share_format_square
            ShareFormat.LINK -> Res.string.share_format_link
        },
    )
    JaugeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenAnalysis(item.share.analysisId) },
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (analysis != null) {
                JaugeGauge(score = analysis.score, size = GaugeSize.MINI, animate = false)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = analysis?.jobTitle ?: stringResource(Res.string.shares_unknown_title),
                    style = JaugeTheme.typography.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.share.url.removePrefix("https://"),
                    style = JaugeTheme.typography.numberCaption,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = joinWithDots(
                        analysis?.company.takeUnless { item.share.options.hideCompany },
                        stringResource(Res.string.shares_format_date, formatLabel, date.day, months.getOrElse(date.month.ordinal) { "" }),
                    ),
                    style = JaugeTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = { onCopyLink(item) }, modifier = Modifier.size(40.dp)) {
                Icon(JaugeIcons.Copy, contentDescription = stringResource(Res.string.shares_copy_link), tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(40.dp)) {
                Icon(JaugeIcons.Trash, contentDescription = stringResource(Res.string.shares_delete), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
