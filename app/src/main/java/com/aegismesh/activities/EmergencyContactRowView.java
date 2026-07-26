package com.aegismesh.activities;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class EmergencyContactRowView extends LinearLayout {
    private final EditText inputName;
    private final EditText inputPhone;
    private EmergencyContactRowView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        setPadding(0, pad, 0, pad);
        inputName = new EditText(context);
        inputName.setHint("Contact name");
        addView(inputName, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        inputPhone = new EditText(context);
        inputPhone.setHint("Phone number");
        inputPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        LayoutParams phoneParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        phoneParams.setMarginStart(pad);
        addView(inputPhone, phoneParams);
        ImageButton buttonRemove = new ImageButton(context);
        buttonRemove.setImageResource(android.R.drawable.ic_menu_delete);
        buttonRemove.setBackgroundColor(0);
        buttonRemove.setOnClickListener(v -> {
            View parentView = (View) getParent();
            if (parentView instanceof ViewGroup) {
                ((ViewGroup) parentView).removeView(EmergencyContactRowView.this);
            }
        });
        addView(buttonRemove);
    }
    public static EmergencyContactRowView newInstance(Context context) {
        return new EmergencyContactRowView(context);
    }
    public String getContactName() { return inputName.getText().toString().trim(); }
    public String getContactPhone() { return inputPhone.getText().toString().trim(); }
}
