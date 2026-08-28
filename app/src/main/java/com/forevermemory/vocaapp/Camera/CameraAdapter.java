package com.forevermemory.vocaapp.Camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.forevermemory.vocaapp.R;

import java.util.List;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.PhotoViewHolder> {

    private List<Bitmap> photos;
    private static final int MAX_PHOTOS = 10;

    public CameraAdapter(List<Bitmap> photos) {
        this.photos = photos;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        holder.photoImageView.setImageBitmap(photos.get(position));
        // 삭제 버튼 클릭 리스너
        holder.deleteImageView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                removePhoto(adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    // 사진의 개수를 10개 이하로 제한
    public void addPhoto(Bitmap photo, Context context) {
        if (photos.size() >= MAX_PHOTOS) {
            Toast.makeText(context, "사진은 10개까지 선택 가능합니다.", Toast.LENGTH_SHORT).show();
            return;
        } else {
            photos.add(photo);
            notifyItemInserted(photos.size() - 1);
        }
    }

    // 사진 삭제
    public void removePhoto(int position) {
        if (position >= 0 && position < photos.size()) {
            photos.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, photos.size());
        }
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        ImageView deleteImageView;


        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            deleteImageView = itemView.findViewById(R.id.deleteImageView);
        }
    }
}
