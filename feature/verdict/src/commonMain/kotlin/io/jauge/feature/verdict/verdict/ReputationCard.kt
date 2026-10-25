package io.jauge.feature.verdict.verdict

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.common.link.WebLinks
import io.jauge.core.designsystem.component.JaugeCard
import io.jauge.core.designsystem.component.JaugePill
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.Reputation
import io.jauge.core.domain.model.ReputationSource
import io.jauge.core.domain.model.ReputationTone
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.verdict_reputation_link_description
import io.jauge.feature.verdict.resources.verdict_reputation_mentions
import io.jauge.feature.verdict.resources.verdict_reputation_source_reddit
import io.jauge.feature.verdict.resources.verdict_reputation_source_x
import io.jauge.feature.verdict.resources.verdict_reputation_title
import io.jauge.feature.verdict.resources.verdict_reputation_tone_mixed
import io.jauge.feature.verdict.resources.verdict_reputation_tone_negative
import io.jauge.feature.verdict.resources.verdict_reputation_tone_positive
import io.jauge.feature.verdict.resources.verdict_reputation_tone_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A link shown under the reputation: a post the summary relies on, or the public search of the site when the server
 * sent no post.
 *
 * @property source site of the link.
 * @property url `https` link, already checked.
 * @property number number of the post for its site (1, 2...), null for a single link of that site.
 */
internal data class ReputationLinkItem(val source: ReputationSource, val url: String, val number: Int?)

/**
 * Links of the card: the posts of [reputation] when it has some, otherwise one search per source for [searchQuery].
 *
 * @param reputation the reputation.
 * @param searchQuery company name (or job title) searched on the sites.
 */
internal fun reputationLinkItems(reputation: Reputation, searchQuery: String): List<ReputationLinkItem> {
    if (reputation.links.isNotEmpty()) {
        val countBySource = reputation.links.groupingBy { it.source }.eachCount()
        val seen = mutableMapOf<ReputationSource, Int>()
        return reputation.links.mapNotNull { link ->
            val url = WebLinks.safeWebLinkOrNull(link.url) ?: return@mapNotNull null
            val number = (seen[link.source] ?: 0) + 1
            seen[link.source] = number
            ReputationLinkItem(link.source, url, number.takeIf { (countBySource[link.source] ?: 0) > 1 })
        }
    }
    if (searchQuery.isBlank()) return emptyList()
    return reputation.sources.mapNotNull { source ->
        WebLinks.safeWebLinkOrNull(source.searchUrl(searchQuery))?.let { ReputationLinkItem(source, it, null) }
    }
}

/**
 * "Ce qu'en disent les candidats": what public posts (Reddit, X) say about the company, summed up by the server, with
 * its tone and the links of its sources (mockup 04, below the key figures). Shown only when the API sends a
 * reputation; follows the light and dark themes through the design system tokens.
 *
 * @param reputation the reputation.
 * @param searchQuery company name (or job title), used when the server sent no post link.
 * @param modifier modifier of the card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReputationCard(reputation: Reputation, searchQuery: String, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    val uriHandler = LocalUriHandler.current
    val links = reputationLinkItems(reputation, searchQuery)
    JaugeCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JaugeIcons.Message, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(Res.string.verdict_reputation_title),
                style = JaugeTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TonePill(reputation.tone)
        }
        Text(
            text = reputation.summary,
            style = JaugeTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (reputation.mentionCount > 0) {
            Text(
                text = pluralStringResource(Res.plurals.verdict_reputation_mentions, reputation.mentionCount, reputation.mentionCount),
                style = JaugeTheme.typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (links.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                links.forEach { item ->
                    SourceLink(item = item, onOpen = { runCatching { uriHandler.openUri(item.url) } })
                }
            }
        }
    }
}

@Composable
private fun TonePill(tone: ReputationTone) {
    val colors = JaugeTheme.colors
    val (label, container, content) = when (tone) {
        ReputationTone.POSITIVE -> Triple(Res.string.verdict_reputation_tone_positive, colors.calm.container, colors.calm.content)
        ReputationTone.MIXED -> Triple(Res.string.verdict_reputation_tone_mixed, colors.disputed.container, colors.disputed.content)
        ReputationTone.NEGATIVE -> Triple(Res.string.verdict_reputation_tone_negative, colors.crowded.container, colors.crowded.content)
        ReputationTone.UNKNOWN -> Triple(Res.string.verdict_reputation_tone_unknown, colors.backgroundAlternative, colors.textSecondary)
    }
    JaugePill(text = stringResource(label), containerColor = container, contentColor = content)
}

@Composable
private fun SourceLink(item: ReputationLinkItem, onOpen: () -> Unit) {
    val colors = JaugeTheme.colors
    val name = stringResource(item.source.label)
    val label = item.number?.let { "$name $it" } ?: name
    val description = stringResource(Res.string.verdict_reputation_link_description, label)
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(JaugeTheme.radii.pillShape)
            .border(1.dp, colors.outline, JaugeTheme.radii.pillShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(JaugeIcons.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(text = label, style = JaugeTheme.typography.pill, color = colors.textPrimary)
    }
}

/** Name of the site shown on its link. */
private val ReputationSource.label: StringResource
    get() = when (this) {
        ReputationSource.REDDIT -> Res.string.verdict_reputation_source_reddit
        ReputationSource.X -> Res.string.verdict_reputation_source_x
    }
