package com.forevermemory.vocaapp.Onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;

/**
 * 웰컴 배너에서 진입하는 4페이지 튜토리얼 캐러셀 어댑터.
 * 정적 콘텐츠라 페이지 문구는 여기 상수로 들고 있는다.
 */
class TutorialPagerAdapter extends RecyclerView.Adapter<TutorialPagerAdapter.PageViewHolder> {

    static final String[] TITLES = {
            "분명 외웠는데, 왜 자꾸 까먹게 될까요",
            "에빙하우스 망각곡선",
            "곡선을 늦추는 방법, 간격 반복",
            "보카앱이 도와드리는 방법",
    };

    static final String[] BODIES = {
            "단어장을 펴고 한 시간 동안 30개를 열심히 외웠어요. 뿌듯한 마음으로 책을 덮었는데, 다음 날이 되면 절반도 기억나지 않아요. 일주일쯤 지나면 처음 보는 단어처럼 낯설게 느껴지고요. 혹시 이런 경험 있으셨나요? 걱정하지 않으셔도 돼요. 의지력이나 암기력이 부족해서가 아니라, 우리 뇌가 원래 그렇게 작동하기 때문이거든요.",
            "1885년, 독일의 심리학자 헤르만 에빙하우스는 스스로를 실험 대상으로 삼아 \"우리는 얼마나 빨리, 얼마나 많이 잊어버릴까\"를 직접 측정해봤어요. 새로 배운 내용은 학습 직후부터 아주 빠르게 사라져요. 하루만 지나도 상당 부분이 기억에서 빠져나가고, 복습 없이 시간이 흐르면 결국 대부분을 잊어버리게 되죠. 이렇게 가파르게 떨어지다가 점점 완만해지는 곡선을 망각곡선(Forgetting Curve)이라고 불러요. 망각은 배운 직후부터, 그것도 아주 빠르게 시작된다는 게 핵심이에요.",
            "잊어버리기 직전, 바로 그 타이밍에 한 번만 다시 떠올려도 곡선이 훨씬 완만해지면서 다음번엔 더 오래 기억이 유지돼요. 이걸 반복하면 복습할 때마다 \"잊어버리는 데 걸리는 시간\"이 점점 길어져요. 처음엔 하루 만에 잊어버렸던 단어가, 몇 번의 복습을 거치면 한 달이 지나도 자연스럽게 떠오르는 단어가 되는 거죠. 이렇게 단기기억이 장기기억으로 옮겨가는 과정을 간격 반복 학습(Spaced Repetition)이라고 해요.",
            "보카앱은 이 원리를 앱 안에 그대로 담았어요. 단어 카드의 학습 상태 버튼을 눌러 \"아직 헷갈려요\" 또는 \"이제 확실히 알아요\"로 표시해두시면, 아직 헷갈리는 단어 위주로 다시 볼 수 있어요. 그리고 정해진 주기로 복습 알림을 보내드려서, 잊어버리기 직전의 순간을 그냥 흘려보내지 않게 해드려요. 복습 알림이 왔을 때 \"나중에\"라고 미루지 않는 것, 그게 오늘 외운 단어를 오래 기억하는 가장 확실한 방법이에요.",
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
