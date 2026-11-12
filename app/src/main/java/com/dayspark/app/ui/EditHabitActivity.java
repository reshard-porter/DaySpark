package com.dayspark.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.dayspark.app.R;
import com.dayspark.app.data.Habit;
import com.dayspark.app.data.HabitRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.format.TextStyle;

/** Create a habit, or edit / delete an existing one. */
public final class EditHabitActivity extends BaseActivity {

    public static final String EXTRA_HABIT_ID = "habit_id";

    private HabitRepository repo;
    private Habit habit = new Habit();
    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private TextInputEditText noteInput;
    private ChipGroup dayChips;
    private TextView daysError;
    private MaterialButton saveButton;
    private View root;
    private boolean saving;
    private String originalName = "";
    private String originalNote = "";
    private int originalMask = Habit.EVERY_DAY;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_habit);
        root = findViewById(R.id.root);
        applySystemInsets(root);
        repo = HabitRepository.get(this);

        nameLayout = findViewById(R.id.name_layout);
        nameInput = findViewById(R.id.name_input);
        noteInput = findViewById(R.id.note_input);
        dayChips = findViewById(R.id.day_chips);
        daysError = findViewById(R.id.days_error);
        saveButton = findViewById(R.id.save_button);

        long id = getIntent().getLongExtra(EXTRA_HABIT_ID, 0);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setUpToolbar(toolbar, true);
        toolbar.setTitle(id == 0 ? R.string.title_new_habit : R.string.title_edit_habit);
        if (id != 0) {
            toolbar.inflateMenu(R.menu.menu_edit);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_delete) {
                    confirmDelete();
                    return true;
                }
                return false;
            });
        }

        buildDayChips();
        findViewById(R.id.preset_every_day).setOnClickListener(v -> setMask(Habit.EVERY_DAY));
        findViewById(R.id.preset_weekdays).setOnClickListener(v -> setMask(Habit.WEEKDAYS));
        nameInput.setOnFocusChangeListener((v, focused) -> { if (focused) nameLayout.setError(null); });
        saveButton.setOnClickListener(v -> save());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasChanges()) confirmDiscard();
                else finish();
            }
        });

        if (id != 0 && savedInstanceState == null) {
            saveButton.setEnabled(false);
            repo.loadHabit(id, loaded -> {
                saveButton.setEnabled(true);
                if (loaded == null) {
                    finish();
                    return;
                }
                habit = loaded;
                originalName = loaded.name;
                originalNote = loaded.note;
                originalMask = loaded.daysMask;
                nameInput.setText(loaded.name);
                noteInput.setText(loaded.note);
                setMask(loaded.daysMask);
            });
        } else if (id != 0) {
            habit.id = id;
        } else if (savedInstanceState == null) {
            setMask(Habit.EVERY_DAY);
            nameInput.requestFocus();
        }
    }

    private void buildDayChips() {
        for (int i = 0; i < 7; i++) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(Formatters.shortDayName(this, i, TextStyle.SHORT));
            chip.setContentDescription(Formatters.shortDayName(this, i, TextStyle.FULL));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setTag(i);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) daysError.setVisibility(View.GONE);
            });
            dayChips.addView(chip);
        }
    }

    private void setMask(int mask) {
        for (int i = 0; i < dayChips.getChildCount(); i++) {
            Chip chip = (Chip) dayChips.getChildAt(i);
            chip.setChecked((mask & (1 << (int) chip.getTag())) != 0);
        }
    }

    private int currentMask() {
        int mask = 0;
        for (int i = 0; i < dayChips.getChildCount(); i++) {
            Chip chip = (Chip) dayChips.getChildAt(i);
            if (chip.isChecked()) mask |= 1 << (int) chip.getTag();
        }
        return mask;
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private boolean hasChanges() {
        return !text(nameInput).equals(originalName.trim())
                || !text(noteInput).equals(originalNote.trim())
                || currentMask() != originalMask;
    }

    private void save() {
        if (saving) return;
        String name = text(nameInput);
        int mask = currentMask();
        boolean valid = true;
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_name_required));
            nameInput.requestFocus();
            valid = false;
        } else {
            nameLayout.setError(null);
        }
        if (mask == 0) {
            daysError.setVisibility(View.VISIBLE);
            valid = false;
        }
        if (!valid) return;

        saving = true;
        saveButton.setEnabled(false);
        boolean isNew = habit.id == 0;
        habit.name = name;
        habit.note = text(noteInput);
        habit.daysMask = mask;
        repo.saveHabit(habit, id -> {
            saving = false;
            saveButton.setEnabled(true);
            if (id == null) {
                showMessage(root, getString(R.string.error_save_habit));
                return;
            }
            setResult(RESULT_OK, new Intent().putExtra(MainActivity.EXTRA_MESSAGE,
                    isNew ? R.string.msg_habit_created : R.string.msg_habit_updated));
            finish();
        });
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_discard_title)
                .setMessage(R.string.dialog_discard_body)
                .setPositiveButton(R.string.action_discard, (d, w) -> finish())
                .setNegativeButton(R.string.action_keep_editing, null)
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_delete_title, text(nameInput)))
                .setMessage(R.string.dialog_delete_body)
                .setPositiveButton(R.string.action_delete, (d, w) -> repo.deleteHabit(habit.id, ok -> {
                    if (ok == null || !ok) {
                        showMessage(root, getString(R.string.error_delete_habit));
                        return;
                    }
                    Intent home = new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(home);
                    finish();
                }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
