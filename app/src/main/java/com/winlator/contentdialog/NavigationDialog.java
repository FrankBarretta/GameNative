package com.winlator.contentdialog;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.navigation.NavigationView;
import app.gamenative.R;
import com.winlator.inputcontrols.ControllerManager;

public class NavigationDialog extends ContentDialog {

    public static final int ACTION_KEYBOARD = 1;
    public static final int ACTION_INPUT_CONTROLS = 2;
    public static final int ACTION_EXIT_GAME = 3;
    public static final int ACTION_EDIT_CONTROLS = 4;
    public static final int ACTION_EDIT_PHYSICAL_CONTROLLER = 5;

    public interface NavigationListener {
        void onNavigationItemSelected(int itemId);
    }

    public NavigationDialog(@NonNull Context context, NavigationListener listener, boolean controlsVisible) {
        super(context, R.layout.navigation_dialog);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(R.drawable.navigation_dialog_background);
        }
        // Hide the title bar and bottom bar for a clean menu-only dialog
        findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        GridLayout grid = findViewById(R.id.main_menu_grid);
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            grid.setColumnCount(5);
        } else {
            grid.setColumnCount(2);
        }

        // Check if physical controller is connected
        ControllerManager controllerManager = ControllerManager.getInstance();
        controllerManager.scanForDevices();
        boolean hasPhysicalController = !controllerManager.getDetectedDevices().isEmpty();

        addMenuItem(context, grid, R.drawable.icon_keyboard, R.string.keyboard, null, ACTION_KEYBOARD, listener, 1.0f, null);
        // Grey out on-screen controls icon when hidden
        addMenuItem(context, grid, R.drawable.icon_input_controls, R.string.input_controls, null, ACTION_INPUT_CONTROLS, listener, controlsVisible ? 1.0f : 0.4f, null);
        addMenuItem(context, grid, R.drawable.icon_popup_menu_edit, R.string.edit_controls, null, ACTION_EDIT_CONTROLS, listener, 1.0f, null);
        // Show physical controller in red with "Disconnected" if no controller is plugged in
        if (hasPhysicalController) {
            addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.edit_physical_controller, null, ACTION_EDIT_PHYSICAL_CONTROLLER, listener, 1.0f, null);
        } else {
            addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.edit_physical_controller, R.string.controller_disconnected, ACTION_EDIT_PHYSICAL_CONTROLLER, listener, 1.0f, Color.RED);
        }
        addMenuItem(context, grid, R.drawable.icon_exit, R.string.exit_game, null, ACTION_EXIT_GAME, listener, 1.0f, null);
    }

    private void addMenuItem(Context context, GridLayout grid, int iconRes, int titleRes, Integer subtitleRes, int itemId, NavigationListener listener, float alpha, Integer customColor) {
        int padding = dpToPx(5, context);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnClickListener(view -> {
            listener.onNavigationItemSelected(itemId);
            dismiss();
        });

        int size = dpToPx(40, context);
        View icon = new View(context);
        icon.setBackground(AppCompatResources.getDrawable(context, iconRes));
        if (icon.getBackground() != null) {
            if (customColor != null) {
                icon.getBackground().setTint(customColor);
            } else {
                icon.getBackground().setTint(context.getColor(R.color.navigation_dialog_item_color));
            }
        }
        icon.setAlpha(alpha); // Apply alpha for greying out
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(lp);
        layout.addView(icon);

        int width = dpToPx(96, context);
        TextView text = new TextView(context);
        text.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setText(context.getString(titleRes));
        text.setGravity(Gravity.CENTER);
        text.setLines(2);
        if (customColor != null) {
            text.setTextColor(customColor);
        } else {
            text.setTextColor(context.getColor(R.color.navigation_dialog_item_color));
        }
        text.setAlpha(alpha); // Apply alpha for greying out
        Typeface tf = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_regular);
        if (tf != null) {
            text.setTypeface(tf);
        }
        layout.addView(text);

        // Add subtitle if provided
        if (subtitleRes != null) {
            TextView subtitle = new TextView(context);
            subtitle.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
            subtitle.setText(context.getString(subtitleRes));
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setLines(1);
            subtitle.setTextSize(10);
            if (customColor != null) {
                subtitle.setTextColor(customColor);
            } else {
                subtitle.setTextColor(context.getColor(R.color.navigation_dialog_item_color));
            }
            subtitle.setAlpha(alpha * 0.8f); // Slightly more transparent for subtitle
            if (tf != null) {
                subtitle.setTypeface(tf);
            }
            layout.addView(subtitle);
        }

        grid.addView(layout);
    }


    public int dpToPx(float dp, Context context){
        return (int) (dp * context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }
}
