package com.forevermemory.vocaapp.Onboarding;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.forevermemory.vocaapp.R;

/**
 * 최초 로그인 시 웰컴 배너를 탭하면 열리는 온보딩 튜토리얼.
 *
 * 스와이프형 4페이지 + 하단 점 인디케이터 + "다음"/"건너뛰기" 구성이며,
 * 마지막 페이지에서는 버튼이 "시작하기"로 바뀌고 누르면 화면을 닫는다.
 * 재진입 경로는 없으므로 별도 플래그 없이 배너 쪽에서만 진입을 통제한다.
 */
public class TutorialActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private LinearLayout dots;
    private TextView btnNext;
    private TextView btnSkip;
    private View[] dotViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        // 이 화면이 실제로 떠서 사용자가 닫았다는 신호. 어떤 경로로 닫히든(다음·건너뛰기·
        // 뒤로가기) 호출한 쪽은 RESULT_OK 를 받는다. 화면이 뜨기도 전에 시스템이 주는
        // 취소 결과(RESULT_CANCELED)와 구분하기 위한 것이다.
        setResult(RESULT_OK);

        pager = findViewById(R.id.tutorialPager);
        dots = findViewById(R.id.tutorialDots);
        btnNext = findViewById(R.id.btnTutorialNext);
        btnSkip = findViewById(R.id.btnTutorialSkip);

        pager.setAdapter(new TutorialPagerAdapter());

        buildDots(TutorialPagerAdapter.pageCount());
        updateForPage(0);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateForPage(position);
            }
        });

        btnSkip.setOnClickListener(v -> finish());

        btnNext.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < TutorialPagerAdapter.pageCount() - 1) {
                pager.setCurrentItem(current + 1, true);
            } else {
                finish();
            }
        });
    }

    private void buildDots(int count) {
        dotViews = new View[count];
        int size = dp(8);
        int margin = dp(4);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.tutorial_dot_inactive);
            dots.addView(dot);
            dotViews[i] = dot;
        }
    }

    private void updateForPage(int position) {
        for (int i = 0; i < dotViews.length; i++) {
            dotViews[i].setBackgroundResource(i == position
                    ? R.drawable.tutorial_dot_active
                    : R.drawable.tutorial_dot_inactive);
        }

        boolean lastPage = position == TutorialPagerAdapter.pageCount() - 1;
        btnNext.setText(lastPage ? "시작하기" : "다음");
        btnSkip.setVisibility(lastPage ? View.INVISIBLE : View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
