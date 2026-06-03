package com.example.vocaapp.VocabularyList;

import android.os.Parcel;
import android.os.Parcelable;

public class WordItem implements Parcelable {
    public String word;
    public String meaning;
    public String pronunciation;
    public boolean selected = true; // 기본값: 선택됨
    public String docId;

    public WordItem() {
    }

    public WordItem(String word, String meaning, String pronunciation) {
        this.word = word;
        this.meaning = meaning;
        this.pronunciation = pronunciation;
    }

    // Parcel에서 읽어오는 생성자
    protected WordItem(Parcel in) {
        word = in.readString();
        meaning = in.readString();
        pronunciation = in.readString();
        selected = in.readByte() != 0;
        docId = in.readString();
    }

    // Parcel에 쓰는 메서드
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(word);
        dest.writeString(meaning);
        dest.writeString(pronunciation);
        dest.writeByte((byte) (selected ? 1 : 0));
        dest.writeString(docId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // CREATOR (필수)
    public static final Creator<WordItem> CREATOR = new Creator<WordItem>() {
        @Override
        public WordItem createFromParcel(Parcel in) {
            return new WordItem(in);
        }

        @Override
        public WordItem[] newArray(int size) {
            return new WordItem[size];
        }
    };
}