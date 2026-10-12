package com.su.launcherlayout;

import android.view.View;

/** 主页 / 备份 / 设置 三个页面的基类。视图懒建并缓存，切页不重建。 */
abstract class Page {

    protected final MainActivity host;
    private View mView;

    Page(MainActivity h) {
        this.host = h;
    }

    final View view() {
        if (mView == null) {
            mView = build();
        }
        return mView;
    }

    protected abstract View build();

    /** 每次切到本页时调用。 */
    void onShow() {
    }

    void onResult(String op, boolean ok, String msg, String dir, String[] items, String[] grids) {
    }

    void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
    }

    /** 用户用系统文件选择器选定了目录。 */
    void onDirPicked(String path) {
    }
}
