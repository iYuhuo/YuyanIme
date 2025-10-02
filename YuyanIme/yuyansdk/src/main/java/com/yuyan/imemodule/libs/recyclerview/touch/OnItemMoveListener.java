package com.yuyan.imemodule.libs.recyclerview.touch;

import androidx.recyclerview.widget.RecyclerView;

public interface OnItemMoveListener {

    
    boolean onItemMove(RecyclerView.ViewHolder srcHolder, RecyclerView.ViewHolder targetHolder);

    
    void onItemDismiss(RecyclerView.ViewHolder srcHolder);

}