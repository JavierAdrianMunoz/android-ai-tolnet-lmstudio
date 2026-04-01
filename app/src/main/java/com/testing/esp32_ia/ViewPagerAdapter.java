package com.testing.esp32_ia;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(FragmentActivity fa){
        super(fa);
    }

    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new CaptureFragment();
            case 1:
                return new StreamFragment();
            case 2:
                return new WebCamFragment();
            default:
                return new CaptureFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}