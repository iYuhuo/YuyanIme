package com.yuyan.imemodule.libs.recyclerview;

interface Controller {

    
    boolean isMenuOpen();

    
    boolean isLeftMenuOpen();

    
    boolean isRightMenuOpen();

    
    boolean isLeftCompleteOpen();

    
    boolean isRightCompleteOpen();

    
    boolean isMenuOpenNotEqual();

    
    boolean isLeftMenuOpenNotEqual();

    
    boolean isRightMenuOpenNotEqual();

    
    void smoothOpenMenu();

    
    void smoothCloseMenu();

    
    void smoothCloseMenu(int duration);

}