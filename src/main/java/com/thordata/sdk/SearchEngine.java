package com.thordata.sdk;

import java.util.Arrays;

/** Known SERP engines; unknown future engines can still be passed as strings for validation errors. */
public enum SearchEngine {
    GOOGLE("google"), GOOGLE_SEARCH("google_search"), GOOGLE_LIGHT("google_light"),
    GOOGLE_WEB("google_web"), GOOGLE_IMAGES("google_images"), GOOGLE_VIDEOS("google_videos"),
    GOOGLE_NEWS("google_news"), GOOGLE_SHOPPING("google_shopping"), GOOGLE_LOCAL("google_local"),
    GOOGLE_PLACES("google_places"), GOOGLE_PRODUCT("google_product"), GOOGLE_LENS("google_lens"),
    GOOGLE_TRENDS("google_trends"), GOOGLE_MAPS("google_maps"), GOOGLE_HOTELS("google_hotels"),
    GOOGLE_JOBS("google_jobs"), GOOGLE_SCHOLAR("google_scholar"), GOOGLE_SCHOLAR_CITE("google_scholar_cite"),
    GOOGLE_SCHOLAR_AUTHOR("google_scholar_author"), GOOGLE_PLAY("google_play"),
    GOOGLE_PLAY_PRODUCT("google_play_product"), GOOGLE_PLAY_GAMES("google_play_games"),
    GOOGLE_PLAY_MOVIES("google_play_movies"), GOOGLE_PLAY_BOOKS("google_play_books"),
    GOOGLE_FINANCE("google_finance"), GOOGLE_FINANCE_MARKETS("google_finance_markets"),
    GOOGLE_FLIGHTS("google_flights"), GOOGLE_PATENTS("google_patents"),
    GOOGLE_PATENTS_DETAILS("google_patents_details"), GOOGLE_AI_MODE("google_ai_mode"),
    BING("bing"), BING_IMAGES("bing_images"), BING_VIDEOS("bing_videos"), BING_NEWS("bing_news"),
    BING_SHOPPING("bing_shopping"), BING_MAPS("bing_maps"), YANDEX("yandex"), DUCKDUCKGO("duckduckgo");

    private final String value;

    SearchEngine(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SearchEngine fromValue(String value) {
        return Arrays.stream(values()).filter(engine -> engine.value.equals(value)).findFirst().orElse(null);
    }
}
