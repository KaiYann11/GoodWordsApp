package com.codex.appgoodwords.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 앱의 색.
 *
 * **바탕은 거의 흰색, 카드는 완전한 흰색입니다.** 그래야 카드가 떠 보이고, 그림자를 옅게 줘도
 * 경계가 읽힙니다. 회색 바탕에 회색 카드를 얹으면 선을 굵게 그어야 나뉘고, 선이 굵어지면
 * 화면이 빽빽해 보입니다.
 *
 * **짙은 파랑 하나가 주인공입니다.** 누를 수 있는 것과 지금 중요한 숫자에만 씁니다. 색을 여기저기
 * 쓰면 어디를 봐야 할지 알 수 없어져서, 화면이 화려해질수록 오히려 밋밋해 보입니다.
 * 나머지 색은 걸음마다 다른 표시로만 씁니다([StepPalette]).
 */

/** 누를 수 있는 것과 지금 중요한 숫자. */
val Indigo = Color(0xFF3B5BFF)
val IndigoDeep = Color(0xFF2440C7)
val IndigoSoft = Color(0xFFE8ECFF)

/** 글자. 완전한 검정 대신 남색을 섞어 화면이 덜 딱딱해 보이게 합니다. */
val Ink = Color(0xFF16181D)
val InkMuted = Color(0xFF6B7180)
val InkFaint = Color(0xFF9AA0AE)

/** 바탕과 카드. */
val Canvas = Color(0xFFF7F8FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceSunken = Color(0xFFF0F1F5)
val Hairline = Color(0xFFE8EAEF)

/** 잘한 일·끝낸 일. */
val Mint = Color(0xFF00B894)
val MintSoft = Color(0xFFDFF7F1)

/** 눈길을 끌어야 하는 것(이정표·이어 온 날). */
val Amber = Color(0xFFFF9F1C)
val AmberSoft = Color(0xFFFFF1DC)

/** 지우기처럼 되돌릴 수 없는 것. */
val Rose = Color(0xFFF2545B)
val RoseSoft = Color(0xFFFFE7E8)

/** 책·독서. */
val Violet = Color(0xFF8B5CF6)
val VioletSoft = Color(0xFFF0E9FF)

// 예전 이름들. 아직 이 이름을 쓰는 화면이 남아 있어 한동안 둡니다.
val MidnightBlue = Ink
val OceanBlue = Indigo
val CoralOrange = Rose
val SunGlow = Amber
val MintGlow = Mint
val CloudWhite = Canvas
val CardWhite = Surface
val PanelBlue = IndigoSoft
val PanelCoral = RoseSoft
val PanelYellow = AmberSoft
val SoftInk = Ink
val SoftSlate = InkMuted
