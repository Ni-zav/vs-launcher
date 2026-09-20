package com.vslauncher;

final class SearchResult {
    static final int TYPE_APP = 0;
    static final int TYPE_COMMAND = 1;
    static final int TYPE_DIAL = 2;
    static final int TYPE_URL = 3;
    static final int TYPE_TIMER = 4;
    static final int TYPE_ALARM = 5;
    static final int TYPE_CALC = 6;
    static final int TYPE_WEB = 7;

    final int type;
    final AppEntry app;
    final String id;
    final String label;
    final String meta;
    final String payload;

    private SearchResult(
            int type,
            AppEntry app,
            String id,
            String label,
            String meta,
            String payload
    ) {
        this.type = type;
        this.app = app;
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.meta = meta == null ? "" : meta;
        this.payload = payload == null ? "" : payload;
    }

    static SearchResult app(AppEntry app) {
        return new SearchResult(TYPE_APP, app, "", app.label, "", "");
    }

    static SearchResult command(SearchCommand command) {
        String meta = SearchCommand.HELP.equals(command.id) ? "GUIDE" : "SYSTEM";
        return new SearchResult(
                TYPE_COMMAND,
                null,
                command.id,
                command.label,
                meta,
                ""
        );
    }

    static SearchResult dial(String number) {
        return new SearchResult(TYPE_DIAL, null, "dial", number, "DIAL", number);
    }

    static SearchResult url(String url) {
        return new SearchResult(TYPE_URL, null, "url", url, "OPEN", url);
    }

    static SearchResult timer(TimeQueryActions.TimerSpec spec) {
        return new SearchResult(
                TYPE_TIMER,
                null,
                "timer",
                "Start " + spec.display + " timer",
                "TIMER",
                Integer.toString(spec.seconds)
        );
    }

    static SearchResult alarm(TimeQueryActions.AlarmSpec spec) {
        return new SearchResult(
                TYPE_ALARM,
                null,
                "alarm",
                "Set alarm " + spec.display,
                "ALARM",
                spec.hour + ":" + spec.minute
        );
    }

    static SearchResult calculation(String result) {
        return new SearchResult(TYPE_CALC, null, "calc", result, "CALC", result);
    }

    static SearchResult web(String query) {
        return new SearchResult(TYPE_WEB, null, "web", "Search web", "WEB", query);
    }

    boolean isApp() {
        return type == TYPE_APP && app != null;
    }

    boolean blocksAppAutoLaunch() {
        if (type == TYPE_APP) return false;
        if (type == TYPE_COMMAND) return SearchCommand.HELP.equals(id);
        return type == TYPE_DIAL
                || type == TYPE_URL
                || type == TYPE_TIMER
                || type == TYPE_ALARM
                || type == TYPE_CALC
                || type == TYPE_WEB;
    }
}
