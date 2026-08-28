package com.forevermemory.vocaapp.Settting;

public class NoticeItem {
    private final String title;
    private final String content;
    private final String date;

    public NoticeItem(String title, String content, String date) {
        this.title = title;
        this.content = content;
        this.date = date;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getDate() { return date; }
}
