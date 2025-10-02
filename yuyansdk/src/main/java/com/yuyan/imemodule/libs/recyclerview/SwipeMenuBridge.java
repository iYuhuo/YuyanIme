package com.yuyan.imemodule.libs.recyclerview;

public class SwipeMenuBridge {

    private final Controller mController;
    private final int mPosition;

    public SwipeMenuBridge(Controller controller, int position) {
        this.mController = controller;
        this.mPosition = position;
    }

    
    public int getPosition() {
        return mPosition;
    }

    public void closeMenu() {
        mController.smoothCloseMenu();
    }
}