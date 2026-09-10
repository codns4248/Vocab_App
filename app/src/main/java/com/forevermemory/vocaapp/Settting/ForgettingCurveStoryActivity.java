package com.forevermemory.vocaapp.Settting;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.forevermemory.vocaapp.R;

/**
 * 설정 > 고객지원 > "망각곡선 이야기".
 *
 * 서버 연동 없이 정적 텍스트만 보여주는 화면이다. 마크다운 렌더링 라이브러리가
 * 코드베이스에 없어서, {@link #STORY} 의 각 줄을 훑으며 "# / ##" 는 제목,
 * 나머지는 문단 TextView 로 그려준다. "**굵게**" 는 굵은 글씨 + 진한 색으로 강조한다.
 * (강조 처리는 AccountRetentionBottomSheet 의 방식과 동일한 Span 조합을 쓴다.)
 */
public class ForgettingCurveStoryActivity extends AppCompatActivity {

    // 화면에 표시할 콘텐츠. 빈 문자열은 문단 구분이다.
    private static final String[] STORY = {
            "# 왜 보카앱은 다른가 — 망각곡선 이야기",
            "",
            "## 분명 외웠는데, 왜 자꾸 까먹게 될까요",
            "",
            "단어장을 펴고 한 시간 동안 30개를 열심히 외웠어요. 뿌듯한 마음으로 책을 덮었는데, 다음 날이 되면 절반도 기억나지 않아요. 일주일쯤 지나면 처음 보는 단어처럼 낯설게 느껴지고요.",
            "",
            "혹시 이런 경험 있으셨나요? 걱정하지 않으셔도 돼요. 의지력이나 암기력이 부족해서가 아니라, 우리 뇌가 원래 그렇게 작동하기 때문이거든요.",
            "",
            "## 에빙하우스 망각곡선",
            "",
            "1885년, 독일의 심리학자 헤르만 에빙하우스는 스스로를 실험 대상으로 삼아 \"우리는 얼마나 빨리, 얼마나 많이 잊어버릴까\"를 직접 측정해봤어요. 그 결과는 꽤 놀라웠답니다.",
            "",
            "새로 배운 내용은 학습 직후부터 아주 빠르게 사라져요. 하루만 지나도 상당 부분이 기억에서 빠져나가고, 복습 없이 시간이 흐르면 결국 대부분을 잊어버리게 되죠. 이렇게 가파르게 떨어지다가 점점 완만해지는 곡선을 **망각곡선(Forgetting Curve)**이라고 불러요.",
            "",
            "여기서 꼭 기억하실 점은 하나예요. **망각은 배운 직후부터, 그것도 아주 빠르게 시작된다는 것.** 그러니 \"나중에 몰아서 복습해야지\"라는 계획은 사실 이미 늦은 계획일 가능성이 커요.",
            "",
            "## 곡선을 늦추는 방법: 간격 반복",
            "",
            "다행히 에빙하우스는 망각을 막는 방법도 함께 찾아냈어요. 잊어버리기 직전, 바로 그 타이밍에 한 번만 다시 떠올려도 곡선이 훨씬 완만해지면서 다음번엔 더 오래 기억이 유지된다는 사실이에요.",
            "",
            "이걸 반복하면 어떻게 될까요? 복습할 때마다 \"잊어버리는 데 걸리는 시간\"이 점점 길어져요. 처음엔 하루 만에 잊어버렸던 단어가, 몇 번의 복습을 거치면 한 달이 지나도 자연스럽게 떠오르는 단어가 되는 거죠. 이렇게 단기기억이 장기기억으로 자리를 옮겨가는 과정을 **간격 반복 학습(Spaced Repetition)**이라고 해요.",
            "",
            "결국 오래 기억하는 분들의 비밀은 더 많이 외운 게 아니라, **적절한 타이밍에 딱 필요한 만큼만 다시 떠올렸다는 것**이었어요.",
            "",
            "## 보카앱이 도와드리는 방법",
            "",
            "보카앱은 이 원리를 앱 안에 그대로 담았어요.",
            "",
            "사진으로 찍어 저장한 단어는 그냥 목록에 쌓이기만 하지 않아요. 단어 카드의 학습 상태 버튼을 눌러 \"아직 헷갈려요\" 또는 \"이제 확실히 알아요\"로 표시해두시면, 아직 헷갈리는 단어 위주로 다시 볼 수 있어요. 그리고 정해진 주기로 복습 알림을 보내드려서, 잊어버리기 직전의 순간을 그냥 흘려보내지 않게 해드려요.",
            "",
            "한 번에 몰아서 외우고 다시는 들여다보지 않는 것보다, 잊어버리기 전에 딱 한 번씩 제때 복습하시는 편이 훨씬 오래, 훨씬 적은 노력으로 기억에 남아요. 그 \"제때\"를 알림으로 챙겨드리는 게 바로 보카앱이 하는 일이에요.",
            "",
            "## 지금 해주실 일은 하나뿐이에요",
            "",
            "복습 알림이 왔을 때 \"나중에\"라고 미루지 않는 것. 그 몇 분이 오늘 외운 단어를 내일도, 한 달 후에도 편하게 떠올릴 수 있게 해주는 가장 확실한 방법이랍니다.",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgetting_curve_story);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("망각곡선 이야기");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LinearLayout container = findViewById(R.id.storyContainer);
        for (String line : STORY) {
            if (line.isEmpty()) continue;
            if (line.startsWith("# ")) {
                container.addView(makeHeading(line.substring(2), 22f, "#111111",
                        firstChild(container) ? 0 : 28, 12));
            } else if (line.startsWith("## ")) {
                container.addView(makeHeading(line.substring(3), 17f, "#222222",
                        firstChild(container) ? 0 : 26, 8));
            } else {
                container.addView(makeParagraph(line));
            }
        }
    }

    private boolean firstChild(LinearLayout container) {
        return container.getChildCount() == 0;
    }

    private TextView makeHeading(String text, float sizeSp, String colorHex,
                                int marginTopDp, int marginBottomDp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(android.graphics.Color.parseColor(colorHex));
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setLayoutParams(marginParams(marginTopDp, marginBottomDp));
        return tv;
    }

    private TextView makeParagraph(String line) {
        TextView tv = new TextView(this);
        tv.setText(applyBold(line));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tv.setTextColor(android.graphics.Color.parseColor("#444444"));
        tv.setLineSpacing(dp(4), 1f);
        tv.setLayoutParams(marginParams(0, 14));
        return tv;
    }

    private LinearLayout.LayoutParams marginParams(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(dp(topDp));
        lp.bottomMargin = Math.round(dp(bottomDp));
        return lp;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    // "**굵게**" 구간을 굵은 글씨 + 진한 색으로 강조한다.
    private CharSequence applyBold(String raw) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf("**", i);
            if (open < 0) {
                sb.append(raw.substring(i));
                break;
            }
            int close = raw.indexOf("**", open + 2);
            if (close < 0) {
                sb.append(raw.substring(i));
                break;
            }
            sb.append(raw, i, open);
            int start = sb.length();
            sb.append(raw, open + 2, close);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(android.graphics.Color.parseColor("#111111")),
                    start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = close + 2;
        }
        return sb;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
