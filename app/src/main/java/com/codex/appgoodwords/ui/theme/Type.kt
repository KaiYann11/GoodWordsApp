package com.codex.appgoodwords.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 글자.
 *
 * **큰 것은 더 크고 굵게, 작은 것은 더 작고 흐리게.** 크기가 고만고만하면 어디부터 읽을지
 * 알 수 없어 화면이 평평해 보입니다. 한 화면에 굵은 큰 글자는 하나만 둡니다.
 *
 * 숫자가 커질 자리(점수·연속 날수)는 자간을 좁힙니다. 큰 글자는 기본 자간이 벌어져 보여서,
 * 좁혀야 숫자 하나로 읽힙니다.
 *
 * 줄 간격은 [LineHeightStyle]로 위아래를 고르게 나눕니다. 한글은 글자가 네모나서, 기본값대로
 * 두면 문단 첫 줄이 위로 붙어 카드 안에서 글이 치우쳐 보입니다.
 */
private val EvenLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.2).sp,
        lineHeightStyle = EvenLineHeight
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        lineHeightStyle = EvenLineHeight
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = EvenLineHeight
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = EvenLineHeight
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = EvenLineHeight
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = EvenLineHeight
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = EvenLineHeight
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = EvenLineHeight
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = EvenLineHeight
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = EvenLineHeight
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = EvenLineHeight
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        lineHeightStyle = EvenLineHeight
    )
)
