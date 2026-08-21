package com.example.vocaapp.VocabularyBookList;

import java.util.Map;

/**
 * 단어장 문서에 저장된 상태별 단어 수를 화면 문구로 만든다.
 *
 * 카운터는 VocabularyFirestore가 단어 추가/삭제/상태변경 시 증감으로 유지하고,
 * 단어 목록을 열 때 실제 값으로 한 번 맞춰진다.
 * 아직 한 번도 열지 않은 기존 단어장에는 필드가 없어서 0으로 읽히는데,
 * 그 경우 총 개수(wordCount)를 전부 미학습으로 보여준다.
 */
public class BookStatusCounts {

    public static String format(Map<String, Object> book, boolean includeTotal) {
        int total = toInt(book.get("wordCount"));
        int unlearned = toInt(book.get("unlearnedCount"));
        int confused = toInt(book.get("confusedCount"));
        int learned = toInt(book.get("learnedCount"));

        // 카운터가 아직 채워지지 않은 단어장 (필드 자체가 없는 경우)
        if (unlearned + confused + learned == 0 && total > 0) {
            unlearned = total;
        }

        String body = "미학습 " + unlearned + " · 헷갈림 " + confused + " · 학습 " + learned;
        return includeTotal ? "총 " + total + " · " + body : body;
    }

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
