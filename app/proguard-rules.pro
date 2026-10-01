# CalcQuest Kids R8 rules.
# Minification is disabled by default (gradle.properties: calcquest.minify=false).
# Room, DataStore, Navigation and Compose ship their own consumer rules.
# Domain enums are persisted by name(), so keep their names stable.
-keepnames enum com.calcquest.kids.domain.** { *; }
