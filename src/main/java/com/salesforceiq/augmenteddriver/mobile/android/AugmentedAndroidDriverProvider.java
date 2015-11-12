package com.salesforceiq.augmenteddriver.mobile.android;

import com.google.common.base.Preconditions;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Ugly hack
 *
 * Reasoning:
 * <p>
 *     We cannot define the driver as Singleton for performance reasons. One driver is created synchronously after the other.
 *     When we run in parallel, all the tests queue up, and for example, an android takes 50 seconds to be created on SLabs.
 * </p>
 * <p>
 *     We create the first Driver at the beginning of the set up and we assign it with the setter here. Since this class
 *     is Singleton, all the other classes gets this provider, and since the very first thing the test does on the setup
 *     is to set the driver her, all the rests providers will get this one.
 * </p>
 */
@Singleton
public class AugmentedAndroidDriverProvider implements Provider<AugmentedAndroidDriver> {

    private AugmentedAndroidDriver driver;

    @Override
    public AugmentedAndroidDriver get() {
        return Preconditions.checkNotNull(driver);
    }

    public void set(AugmentedAndroidDriver driver) {
        this.driver = driver;
    }
}

