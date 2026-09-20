package com.vslauncher;

import android.content.ComponentName;

final class AppEntry {
    final String label;
    final String normalizedLabel;
    final ComponentName component;
    final String componentKey;

    AppEntry(String label, ComponentName component) {
        this.label = label;
        this.normalizedLabel = label.toLowerCase(java.util.Locale.ROOT);
        this.component = component;
        this.componentKey = component.flattenToString();
    }
}
