package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drp33.quietsignal.model.PromptMemory
import com.drp33.quietsignal.ui.theme.Grove
import com.drp33.quietsignal.ui.theme.Newsreader
import com.drp33.quietsignal.ui.theme.NunitoSans

/**
 * Settings — the gentle-prompts control. A full-screen sheet matching the Grove
 * look: a switch to turn prompts on/off, a "how it works" explainer (with the
 * next memory it would resurface), frequency, and delivery toggles. State is
 * owned by the host so it can persist via [com.drp33.quietsignal.data.SettingsPreferences].
 */
@Composable
fun SettingsSheet(
    promptsEnabled: Boolean,
    frequency: String,
    quietDelivery: Boolean,
    promptBoth: Boolean,
    prompt: PromptMemory?,
    onPromptsChange: (Boolean) -> Unit,
    onFrequencyChange: (String) -> Unit,
    onQuietDeliveryChange: (Boolean) -> Unit,
    onPromptBothChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Grove.Bg),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // header
                Row(
                    modifier = Modifier.fillMaxWidth().background(Grove.Surface).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GroveCircleButton(glyph = "‹", contentDescription = "Back", onClick = onClose)
                    Text(text = "Settings", fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = Grove.Ink)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    SectionLabel("CONVERSATION PROMPTS")
                    SettingsCard {
                        SettingRow(
                            label = "Gentle prompts",
                            desc = "When things go quiet, Grove resurfaces a shared memory and sends it to you both — a little nudge to reconnect.",
                            last = true,
                        ) {
                            GroveSwitch(on = promptsEnabled, onChange = onPromptsChange)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // how-it-works explainer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Grove.Accent.copy(alpha = 0.08f))
                            .border(1.dp, Grove.Accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Grove.Accent), contentAlignment = Alignment.Center) {
                                Text("✨", fontSize = 15.sp)
                            }
                            Text("How prompts work", fontFamily = NunitoSans, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Grove.Ink)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "As the tree rests and leaves gather, Grove quietly picks one old photo or voice note and opens a fresh thread for it — for both of you. No pressure, no loud alerts. Just a warm \"remember this?\".",
                            fontFamily = NunitoSans, fontSize = 13.sp, color = Grove.InkSoft, lineHeight = 19.sp,
                        )
                        if (promptsEnabled && prompt != null) {
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Grove.Surface).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp),
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(if (prompt.type == "photo") Grove.Photo.copy(alpha = 0.18f) else Grove.Voice.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                    Text(if (prompt.type == "photo") "📸" else "🎙", fontSize = 16.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("UP NEXT", fontFamily = NunitoSans, fontSize = 10.5.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = Grove.InkFaint)
                                    Text(
                                        text = "A ${if (prompt.type == "photo") "photo" else "voice note"} from ${prompt.sender}",
                                        fontFamily = NunitoSans, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Grove.Ink, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    SectionLabel("FREQUENCY")
                    SettingsCard {
                        val opts = listOf(
                            Triple("gentle", "Gentle", "After a quiet stretch"),
                            Triple("weekly", "Weekly", "Once a week, max"),
                            Triple("very_quiet", "Only when very quiet", "After a long silence"),
                        )
                        opts.forEachIndexed { i, (value, label, desc) ->
                            FrequencyRow(
                                label = label,
                                desc = desc,
                                selected = frequency == value,
                                enabled = promptsEnabled,
                                last = i == opts.lastIndex,
                                onClick = { if (promptsEnabled) onFrequencyChange(value) },
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    SectionLabel("NOTIFICATIONS")
                    SettingsCard {
                        SettingRow(label = "Quiet delivery", desc = "Prompts arrive as a soft badge, never a loud alert.") {
                            GroveSwitch(on = quietDelivery, onChange = onQuietDeliveryChange)
                        }
                        SettingRow(label = "Send to both people", desc = "Norman and Sadie see the same prompt thread.", last = true) {
                            GroveSwitch(on = promptBoth, onChange = onPromptBothChange)
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Grove · staying close, gently",
                        fontFamily = NunitoSans, fontSize = 12.sp, color = Grove.InkFaint,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = NunitoSans, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        color = Grove.InkFaint, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Grove.Surface),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(label: String, desc: String, last: Boolean = false, trailing: @Composable () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontFamily = NunitoSans, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Grove.Ink)
                Spacer(Modifier.height(3.dp))
                Text(desc, fontFamily = NunitoSans, fontSize = 12.5.sp, color = Grove.InkSoft, lineHeight = 17.sp)
            }
            trailing()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Grove.Line))
    }
}

@Composable
private fun FrequencyRow(label: String, desc: String, selected: Boolean, enabled: Boolean, last: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontFamily = NunitoSans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Grove.Ink else Grove.InkFaint)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontFamily = NunitoSans, fontSize = 12.5.sp, color = Grove.InkSoft)
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Grove.Foliage2 else Color.Transparent)
                    .border(if (selected) 0.dp else 2.dp, if (selected) Color.Transparent else Grove.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Grove.Line))
    }
}
