package com.yuyan.imemodule.libs.recyclerview.touch;

import androidx.recyclerview.widget.ItemTouchHelper;

public class DefaultItemTouchHelper extends ItemTouchHelper {

    private final ItemTouchHelperCallback mItemTouchHelperCallback;

    
    public DefaultItemTouchHelper() {
        this(new ItemTouchHelperCallback());
    }

    
    private DefaultItemTouchHelper(ItemTouchHelperCallback callback) {
        super(callback);
        mItemTouchHelperCallback = callback;
    }

    
    public void setOnItemMoveListener(OnItemMoveListener onItemMoveListener) {
        mItemTouchHelperCallback.setOnItemMoveListener(onItemMoveListener);
    }

    
    public void setLongPressDragEnabled(boolean canDrag) {
        mItemTouchHelperCallback.setLongPressDragEnabled(canDrag);
    }

}