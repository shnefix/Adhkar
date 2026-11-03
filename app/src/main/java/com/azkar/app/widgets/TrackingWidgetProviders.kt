package com.azkar.app.widgets

// Tracking widgets: disabled in the manifest, enabled by NewWidgetUpdater.syncTrackingProviders while a folder is tracked.
class CStreakSmallWidgetProvider : BaseAzkarWidgetProvider(AzkarWidgetFamily.C_STREAK_SMALL)
class CStreakMediumWidgetProvider : BaseAzkarWidgetProvider(AzkarWidgetFamily.C_STREAK_MEDIUM)
class CStreakLargeWidgetProvider : BaseAzkarWidgetProvider(AzkarWidgetFamily.C_STREAK_LARGE)
class GCountdownSmallWidgetProvider : BaseAzkarWidgetProvider(AzkarWidgetFamily.G_COUNTDOWN_SMALL)
class FBentoSmallWidgetProvider : BaseAzkarWidgetProvider(AzkarWidgetFamily.F_BENTO_SMALL)
class TTodayMediumWidgetProvider : BaseAzkarWidgetProvider(AzkarWidgetFamily.T_TODAY_MEDIUM)
