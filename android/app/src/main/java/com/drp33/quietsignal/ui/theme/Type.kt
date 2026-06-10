package com.drp33.quietsignal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.R

/**
 * Grove typography — Newsreader (serif) for headings, Nunito Sans for body,
 * loaded as DOWNLOADABLE fonts via the Google Fonts provider (no bundled .ttf).
 *
 * REQUIRES (one line) in app/build.gradle dependencies:
 *     implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")
 * and res/values/font_certs.xml (provided). See README_GROVE.md.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val Newsreader = FontFamily(
    Font(GoogleFont("Newsreader"), provider, FontWeight.Normal),
    Font(GoogleFont("Newsreader"), provider, FontWeight.Medium),
    Font(GoogleFont("Newsreader"), provider, FontWeight.SemiBold),
)

val NunitoSans = FontFamily(
    Font(GoogleFont("Nunito Sans"), provider, FontWeight.Normal),
    Font(GoogleFont("Nunito Sans"), provider, FontWeight.SemiBold),
    Font(GoogleFont("Nunito Sans"), provider, FontWeight.Bold),
)

private val base = Typography()

val Typography = Typography(
    // headings → Newsreader serif
    displayLarge = base.displayLarge.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    displayMedium = base.displayMedium.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    displaySmall = base.displaySmall.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    headlineLarge = base.headlineLarge.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = Newsreader, fontWeight = FontWeight.Medium),
    // body / labels → Nunito Sans
    titleMedium = base.titleMedium.copy(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = NunitoSans),
    bodySmall = base.bodySmall.copy(fontFamily = NunitoSans),
    labelLarge = base.labelLarge.copy(fontFamily = NunitoSans, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold),
)
