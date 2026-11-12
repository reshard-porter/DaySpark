#!/usr/bin/env python3
"""Regenerates colors.xml and themes.xml from one role table. Run: python3 tools/generate_themes.py"""
import sys
out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/values"
# role -> per-theme hex.  Order: DaySpark, Forest, Ocean, Lavender, Sunset
T = ["dayspark","forest","ocean","lavender","sunset"]
R = {
 "background":       ["#FBF8F3","#F6F8F2","#F3F8FB","#F8F6FC","#FDF6F1"],
 "surface":          ["#FFFFFF"]*5,
 "surface_low":      ["#F7F3EC","#F0F4EB","#ECF3F8","#F2EFF9","#F9EFE8"],
 "surface_mid":      ["#F3EEE6","#EAF0E4","#E5EEF5","#ECE8F5","#F5E8DF"],
 "surface_high":     ["#EEE8DE","#E3EADC","#DDE8F0","#E6E1F1","#F0E0D5"],
 "surface_highest":  ["#E8E1D5","#DCE4D3","#D5E2EB","#DFD9EC","#EAD8CB"],
 "text":             ["#14213D","#1B2A1F","#0B2540","#241A3D","#2B1A14"],
 "text_secondary":   ["#4A5568","#4B5A4F","#46596B","#564C6B","#65504A"],
 "primary":          ["#0F766E","#2F6B3A","#0B6A94","#6B4FA3","#A8432A"],
 "primary_container":["#CCEFEA","#D6E8D2","#CDE8F5","#E6DDF7","#FADCCB"],
 "on_primary_container":["#134E4A","#1E4526","#0A4766","#46307A","#7A2E1B"],
 "outline":          ["#7D838C","#7B877F","#788692","#857E92","#8E7F78"],
 "outline_variant":  ["#E3DDD2","#DBE3D5","#D6E2EA","#E1DCEB","#EDDDD3"],
}
names = {"dayspark":"DaySpark","forest":"Forest","ocean":"Ocean","lavender":"Lavender","sunset":"Sunset"}

c = ['<?xml version="1.0" encoding="utf-8"?>',
     '<!-- Generated from one role table so every theme defines the same roles. -->',
     '<resources>']
for i,t in enumerate(T):
    c.append(f'    <!-- {names[t]} -->')
    for role,vals in R.items():
        c.append(f'    <color name="{t}_{role}">{vals[i]}</color>')
c += ['    <!-- Shared across themes -->',
      '    <color name="ds_on_primary">#FFFFFF</color>',
      '    <color name="ds_error">#B42318</color>',
      '    <color name="ds_on_error">#FFFFFF</color>',
      '    <color name="ds_error_container">#FEE4E2</color>',
      '    <color name="ds_on_error_container">#7A1A12</color>',
      '    <color name="ds_streak">#E8A33D</color>',
      '    <color name="ic_launcher_background">#0F766E</color>',
      '</resources>','']
open(f"{out}/colors.xml","w").write("\n".join(c))

def items(t, ind="        "):
    m = [("colorPrimary","primary"),("colorOnPrimary",None),("colorPrimaryContainer","primary_container"),
         ("colorOnPrimaryContainer","on_primary_container"),("colorSecondary","primary"),("colorOnSecondary",None),
         ("colorSecondaryContainer","primary_container"),("colorOnSecondaryContainer","on_primary_container"),
         ("colorTertiary","primary"),("colorOnTertiary",None),
         ("android:colorBackground","background"),("colorOnBackground","text"),
         ("colorSurface","background"),("colorOnSurface","text"),("colorOnSurfaceVariant","text_secondary"),
         ("colorSurfaceVariant","surface_high"),
         ("colorSurfaceContainerLowest","surface"),("colorSurfaceContainerLow","surface_low"),
         ("colorSurfaceContainer","surface_mid"),("colorSurfaceContainerHigh","surface_high"),
         ("colorSurfaceContainerHighest","surface_highest"),
         ("colorSurfaceInverse","text"),("colorOnSurfaceInverse","background"),("colorPrimaryInverse","primary_container"),
         ("colorOutline","outline"),("colorOutlineVariant","outline_variant"),
         ("android:textColorPrimary","text"),("android:textColorSecondary","text_secondary"),
         ("android:windowBackground","background")]
    s=[]
    for a,r in m:
        v = "@color/ds_on_primary" if r is None else f"@color/{t}_{r}"
        s.append(f'{ind}<item name="{a}">{v}</item>')
    return s

th = ['<?xml version="1.0" encoding="utf-8"?>','<resources>',
'    <!-- Shared structure: typography, shapes and component styles. Colors come from the theme below. -->',
'    <style name="Base.Theme.DaySpark" parent="Theme.Material3.Light.NoActionBar">',
'        <item name="colorError">@color/ds_error</item>',
'        <item name="colorOnError">@color/ds_on_error</item>',
'        <item name="colorErrorContainer">@color/ds_error_container</item>',
'        <item name="colorOnErrorContainer">@color/ds_on_error_container</item>',
'        <item name="dsStreakColor">@color/ds_streak</item>',
'        <item name="textAppearanceHeadlineMedium">@style/TextAppearance.DaySpark.Display</item>',
'        <item name="textAppearanceHeadlineSmall">@style/TextAppearance.DaySpark.Headline</item>',
'        <item name="textAppearanceTitleLarge">@style/TextAppearance.DaySpark.Title</item>',
'        <item name="textAppearanceTitleMedium">@style/TextAppearance.DaySpark.Subtitle</item>',
'        <item name="textAppearanceBodyLarge">@style/TextAppearance.DaySpark.Body</item>',
'        <item name="textAppearanceBodyMedium">@style/TextAppearance.DaySpark.BodySmall</item>',
'        <item name="textAppearanceLabelLarge">@style/TextAppearance.DaySpark.Label</item>',
'        <item name="textAppearanceLabelMedium">@style/TextAppearance.DaySpark.Caption</item>',
'        <item name="materialButtonStyle">@style/Widget.DaySpark.Button</item>',
'        <item name="materialButtonOutlinedStyle">@style/Widget.DaySpark.Button.Outlined</item>',
'        <item name="borderlessButtonStyle">@style/Widget.DaySpark.Button.Text</item>',
'        <item name="materialCardViewStyle">@style/Widget.DaySpark.Card</item>',
'        <item name="toolbarStyle">@style/Widget.DaySpark.Toolbar</item>',
'        <item name="textInputStyle">@style/Widget.DaySpark.TextInput</item>',
'        <item name="chipStyle">@style/Widget.DaySpark.Chip</item>',
'        <item name="shapeAppearanceCornerSmall">@style/ShapeAppearance.DaySpark.Small</item>',
'        <item name="shapeAppearanceCornerMedium">@style/ShapeAppearance.DaySpark.Medium</item>',
'        <item name="shapeAppearanceCornerLarge">@style/ShapeAppearance.DaySpark.Large</item>',
'        <item name="shapeAppearanceCornerExtraLarge">@style/ShapeAppearance.DaySpark.ExtraLarge</item>',
'    </style>','']
for t in T:
    name = "Theme.DaySpark" if t=="dayspark" else f"Theme.DaySpark.{names[t]}"
    th.append(f'    <style name="{name}" parent="Base.Theme.DaySpark">')
    th += items(t)
    th.append('    </style>'); th.append('')
th += ['    <!-- Launch screen (androidx.core:core-splashscreen) -->',
'    <style name="Theme.DaySpark.Starting" parent="Theme.SplashScreen">',
'        <item name="windowSplashScreenBackground">@color/dayspark_background</item>',
'        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash</item>',
'        <item name="postSplashScreenTheme">@style/Theme.DaySpark</item>',
'    </style>','</resources>','']
open(f"{out}/themes.xml","w").write("\n".join(th))
