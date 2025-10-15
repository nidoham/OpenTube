package com.nidoham.opentube.fragments;

import com.nidoham.opentube.R;
import android.content.Context;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base class that provides safe fragment transaction helpers.
 * Helps in loading, replacing, animating, and clearing back stack.
 */
public abstract class BaseStateFragment extends Fragment {

    private static final int CONTAINER_ID = R.id.content_fragment;

    /**
     * Loads a fragment into the container with basic replace transaction.
     */
    public static void loadFragment(@NonNull FragmentManager fragmentManager,
                                    @NonNull Fragment fragment) {
        loadFragment(fragmentManager, fragment, false, null);
    }

    /**
     * Loads a fragment with option to add to back stack.
     */
    public static void loadFragment(@NonNull FragmentManager fragmentManager,
                                    @NonNull Fragment fragment,
                                    boolean addToBackStack) {
        loadFragment(fragmentManager, fragment, addToBackStack, null);
    }

    /**
     * Loads a fragment with full customization options.
     */
    public static void loadFragment(@NonNull FragmentManager fragmentManager,
                                    @NonNull Fragment fragment,
                                    boolean addToBackStack,
                                    @Nullable String tag) {
        if (fragmentManager.isDestroyed()) return;

        FragmentTransaction transaction = fragmentManager.beginTransaction()
                .replace(CONTAINER_ID, fragment, tag);

        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }

        // Safer commit to avoid state loss issues
        commitTransactionSafely(transaction, fragmentManager);
    }

    /**
     * Loads a fragment with custom animations (no pop animations).
     */
    public static void loadFragmentWithAnimation(@NonNull FragmentManager fragmentManager,
                                                 @NonNull Fragment fragment,
                                                 boolean addToBackStack,
                                                 int enterAnim,
                                                 int exitAnim) {
        loadFragmentWithAnimation(fragmentManager, fragment, addToBackStack,
                null, enterAnim, exitAnim, 0, 0);
    }

    /**
     * Loads a fragment with full animation customization.
     */
    public static void loadFragmentWithAnimation(@NonNull FragmentManager fragmentManager,
                                                 @NonNull Fragment fragment,
                                                 boolean addToBackStack,
                                                 @Nullable String tag,
                                                 int enterAnim,
                                                 int exitAnim,
                                                 int popEnterAnim,
                                                 int popExitAnim) {
        if (fragmentManager.isDestroyed()) return;

        FragmentTransaction transaction = fragmentManager.beginTransaction()
                .setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
                .replace(CONTAINER_ID, fragment, tag);

        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }

        commitTransactionSafely(transaction, fragmentManager);
    }

    /**
     * Safely pops the back stack if possible.
     */
    public static boolean popBackStack(@NonNull FragmentManager fragmentManager) {
        if (!fragmentManager.isDestroyed() && fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            return true;
        }
        return false;
    }

    /**
     * Clears the entire back stack.
     */
    public static void clearBackStack(@NonNull FragmentManager fragmentManager) {
        if (!fragmentManager.isDestroyed()) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    /**
     * Safely commits a transaction depending on state.
     */
    private static void commitTransactionSafely(@NonNull FragmentTransaction transaction,
                                                @NonNull FragmentManager fragmentManager) {
        try {
            if (!fragmentManager.isStateSaved()) {
                transaction.commit();
            } else {
                transaction.commitAllowingStateLoss();
            }
        } catch (IllegalStateException e) {
            transaction.commitAllowingStateLoss();
        }
    }
}
