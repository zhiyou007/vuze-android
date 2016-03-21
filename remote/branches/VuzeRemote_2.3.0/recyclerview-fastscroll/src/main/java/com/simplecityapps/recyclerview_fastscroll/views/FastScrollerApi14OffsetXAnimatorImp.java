/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.simplecityapps.recyclerview_fastscroll.views;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Property;

/**
 * Created by nammari on 2/22/16.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class FastScrollerApi14OffsetXAnimatorImp implements FastScrollerOffsetXAnimator {

    private static final Property<FastScroller, Integer> OFFSETX_PROPERTY =
            new Property<FastScroller, Integer>(Integer.class, "offsetX") {
                @Override
                public Integer get(FastScroller fastScroller) {
                    return fastScroller.getOffsetX();
                }

                @Override
                public void set(FastScroller fastScroller, Integer offsetX) {
                    fastScroller.setOffsetX(offsetX);
                }
            };

    @Override
    public ObjectAnimator getOffsetXAnimator(FastScroller target, int... value) {
        return ObjectAnimator.ofInt(target, OFFSETX_PROPERTY, value);

    }
}
