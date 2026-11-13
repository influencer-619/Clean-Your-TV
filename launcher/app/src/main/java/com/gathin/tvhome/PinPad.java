package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * D-pad PIN entry for the Watch timer. Soft keyboards on Android TV leave the remote fighting the
 * dialog buttons; this pad takes only D-pad and OK.
 */
final class PinPad {
    private PinPad() {
    }

    /**
     * @param accept return true to close, false to clear the digits and keep the pad open (wrong PIN)
     * @param forgot null hides the Forgot key
     */
    static void show(Activity activity, int titleRes, int length, Runnable forgot,
                     java.util.function.Predicate<String> accept) {
        View panel = LayoutInflater.from(activity).inflate(R.layout.dialog_pin_pad, null);
        TextView title = panel.findViewById(R.id.pinTitle);
        TextView dots = panel.findViewById(R.id.pinDots);
        title.setText(titleRes);
        StringBuilder typed = new StringBuilder(length);
        Runnable refresh = () -> {
            StringBuilder shown = new StringBuilder(length);
            for (int i = 0; i < length; i++) shown.append(i < typed.length() ? '•' : '–');
            dots.setText(shown);
        };
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        View.OnClickListener digit = v -> {
            if (typed.length() >= length) return;
            typed.append(((TextView) v).getText());
            refresh.run();
            if (typed.length() < length) return;
            if (accept.test(typed.toString())) {
                dialog.dismiss();
                return;
            }
            typed.setLength(0);
            refresh.run();
            Toast.makeText(activity, R.string.watch_timer_wrong, Toast.LENGTH_SHORT).show();
            panel.findViewById(R.id.pin5).requestFocus();
        };

        int[] ids = {R.id.pin0, R.id.pin1, R.id.pin2, R.id.pin3, R.id.pin4,
                R.id.pin5, R.id.pin6, R.id.pin7, R.id.pin8, R.id.pin9};
        for (int id : ids) panel.findViewById(id).setOnClickListener(digit);

        panel.findViewById(R.id.pinClear).setOnClickListener(v -> {
            typed.setLength(0);
            refresh.run();
        });

        View forgotKey = panel.findViewById(R.id.pinForgot);
        if (forgot == null) {
            forgotKey.setVisibility(View.INVISIBLE);
            forgotKey.setFocusable(false);
            forgotKey.setClickable(false);
        } else {
            forgotKey.setOnClickListener(v -> {
                dialog.dismiss();
                forgot.run();
            });
        }

        dialog.setOnShowListener(d -> panel.findViewById(R.id.pin5).requestFocus());
        dialog.show();
    }
}
