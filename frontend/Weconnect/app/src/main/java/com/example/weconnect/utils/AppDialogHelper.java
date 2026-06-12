package com.example.weconnect.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class AppDialogHelper {

    public static final int COLOR_PRIMARY = Color.rgb(255, 77, 109);
    public static final int COLOR_PRIMARY_SOFT = Color.rgb(255, 230, 235);
    public static final int COLOR_DIALOG_BORDER = Color.rgb(255, 213, 224);
    public static final int COLOR_TEXT_SECONDARY = Color.rgb(117, 117, 117);

    private AppDialogHelper() {
    }

    public static AlertDialog showInfo(Context context,
                                       String title,
                                       String message,
                                       String positiveText) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText, null)
                .create();
        showStyled(dialog);
        return dialog;
    }

    public static AlertDialog showConfirm(Context context,
                                          String title,
                                          String message,
                                          String positiveText,
                                          DialogInterface.OnClickListener positiveListener,
                                          String negativeText) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 20));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Color.rgb(35, 35, 35));
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        titleView.setIncludeFontPadding(true);
        container.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextColor(COLOR_TEXT_SECONDARY);
        messageView.setTextSize(16);
        messageView.setLineSpacing(dp(context, 2), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(context, 10);
        container.addView(messageView, messageParams);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(context, 18);
        container.addView(buttonRow, rowParams);

        MaterialButton negativeButton = createDialogButton(context, negativeText, false);
        MaterialButton positiveButton = createDialogButton(context, positiveText, true);

        LinearLayout.LayoutParams negativeParams = new LinearLayout.LayoutParams(
                0, dp(context, 52), 1f);
        LinearLayout.LayoutParams positiveParams = new LinearLayout.LayoutParams(
                0, dp(context, 52), 1f);
        positiveParams.leftMargin = dp(context, 10);
        buttonRow.addView(negativeButton, negativeParams);
        buttonRow.addView(positiveButton, positiveParams);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(container)
                .create();

        negativeButton.setOnClickListener(v -> dialog.dismiss());
        positiveButton.setOnClickListener(v -> {
            if (positiveListener != null) {
                positiveListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
            }
            dialog.dismiss();
        });

        dialog.show();
        styleWindow(dialog);
        return dialog;
    }

    private static MaterialButton createDialogButton(Context context, String text, boolean primary) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        button.setCornerRadius(dp(context, 18));
        button.setRippleColor(ColorStateList.valueOf(COLOR_DIALOG_BORDER));
        button.setTextColor(primary ? Color.WHITE : COLOR_PRIMARY);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? COLOR_PRIMARY : COLOR_PRIMARY_SOFT));
        button.setStrokeWidth(primary ? 0 : dp(context, 1));
        button.setStrokeColor(ColorStateList.valueOf(COLOR_DIALOG_BORDER));
        button.setStateListAnimator(null);
        return button;
    }

    public static void showStyled(AlertDialog dialog) {
        dialog.setOnShowListener(d -> styleShownDialog(dialog));
        dialog.show();
        styleWindow(dialog);
    }

    public static void styleShownDialog(AlertDialog dialog) {
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            styleButton(positive, true);
        }

        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            styleButton(negative, false);
        }

        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null) {
            styleNeutralButton(neutral);
        }
    }

    private static void styleButton(Button button, boolean primary) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setLetterSpacing(0f);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 40));
        button.setMinWidth(dp(context, primary ? 104 : 82));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        button.setTextColor(primary ? Color.WHITE : COLOR_PRIMARY);
        button.setBackground(primary
                ? primaryButtonBackground(context)
                : secondaryButtonBackground(context));
        addButtonMargin(button);
    }

    private static void styleNeutralButton(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setLetterSpacing(0f);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 40));
        button.setMinWidth(dp(context, 72));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setTextColor(COLOR_TEXT_SECONDARY);
        addButtonMargin(button);
    }

    private static void addButtonMargin(Button button) {
        if (button.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
            int gap = dp(button.getContext(), 8);
            params.setMargins(gap, dp(button.getContext(), 4), 0, dp(button.getContext(), 4));
            button.setLayoutParams(params);
        }
    }

    public static void styleWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(dialogBackground(dialog.getContext()));
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.45f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(params);
    }

    public static GradientDrawable dialogBackground(Context context) {
        GradientDrawable drawable = rounded(Color.WHITE, dp(context, 24));
        drawable.setStroke(dp(context, 1), COLOR_DIALOG_BORDER);
        return drawable;
    }

    public static GradientDrawable primaryButtonBackground(Context context) {
        return rounded(COLOR_PRIMARY, dp(context, 16));
    }

    public static GradientDrawable secondaryButtonBackground(Context context) {
        GradientDrawable drawable = rounded(COLOR_PRIMARY_SOFT, dp(context, 16));
        drawable.setStroke(dp(context, 1), COLOR_DIALOG_BORDER);
        return drawable;
    }

    public static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
