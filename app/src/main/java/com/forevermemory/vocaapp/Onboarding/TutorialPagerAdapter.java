package com.forevermemory.vocaapp.Onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;

/**
 * 웰컴 배너에서 진입하는 3페이지 튜토리얼 캐러셀 어댑터.
 * 정적 콘텐츠라 페이지 문구는 여기 상수로 들고 있는다.
 */
class TutorialPagerAdapter extends RecyclerView.Adapter<TutorialPagerAdapter.PageViewHolder> {

    static final String[] TITLES = {
            "환영해요! 이제 단어, 제대로 외워볼 시간이에요.",
            "배운 직후부터 우리는 빠르게 잊어버려요.",
            "보카앱이 바로 그 타이밍을 챙겨드릴게요.",
    };

    static final String[] BODIES = {
            "혹시 '에빙하우스의 망각곡선'이라고 들어보셨나요?",
            "근데 잊기 직전에 딱 한 번만 다시 보면, 기억이 훨씬 오래가요.",
            "이제 외운 단어, 진짜로 오래 기억날 거예요!",
    };

    static int pageCount() {
        return TITLES.length;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tutorial_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.title.setText(TITLES[position]);
        holder.body.setText(BODIES[position]);
    }

    @Override
    public int getItemCount() {
        return pageCount();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView body;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvTutorialTitle);
            body = itemView.findViewById(R.id.tvTutorialBody);
        }
    }
}
