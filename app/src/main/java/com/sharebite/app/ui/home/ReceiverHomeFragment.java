package com.sharebite.app.ui.home;

import com.sharebite.app.R;

/** Home for individual receivers: nearby, newest, free and discounted food. */
public class ReceiverHomeFragment extends ConsumerHomeFragment {

    @Override
    protected boolean showDiscountedSection() {
        return true;
    }

    @Override
    protected boolean showCharitySection() {
        return false;
    }

    @Override
    protected int subtitleRes() {
        return R.string.home_subtitle_receiver;
    }
}
