package cam72cam.immersiverailroading.gui.components;

import cam72cam.mod.entity.Player;
import cam72cam.mod.gui.helpers.GUIHelpers;
import cam72cam.mod.gui.screen.Button;
import cam72cam.mod.gui.screen.IScreenBuilder;
import cam72cam.mod.gui.screen.TextField;
import cam72cam.mod.text.TextColor;

import java.util.*;
import java.util.stream.Collectors;

import static cam72cam.immersiverailroading.gui.components.GuiUtils.fitString;

public abstract class ListSelector<T> {
    int width;
    T currentValue;
    Map<String, T> rawOptions;
    int page;
    int pageSize;
    boolean visible;

    TextField search;
    Button pagination;
    List<Button> options;

    Map<Button, T> usableButtons;
    Map<Button, Integer> buttonsX;
    Map<Button, Integer> buttonsY;

    public ListSelector(IScreenBuilder screen, int xOff, int width, int height, T currentValue, Map<String, T> rawOptions) {
        this.width = width;
        this.rawOptions = rawOptions;
        this.currentValue = currentValue;
        this.visible = false;

        int xtop = -GUIHelpers.getScreenWidth() / 2 + xOff;
        int ytop = -GUIHelpers.getScreenHeight() / 4;

        this.search = new TextField(screen, xtop, ytop, width - 1, height);

        this.pagination = new Button(screen, xtop, ytop + height, width + 1, height, "Page") {
            @Override
            public void onClick(Player.Hand hand) {
                ListSelector.this.page += hand == Player.Hand.PRIMARY ? 1 : -1;
                ListSelector.this.updateSearch(ListSelector.this.search.getText());
            }
        };
        this.page = 0;

        this.pageSize = Math.max(1, GUIHelpers.getScreenHeight() / height - 2);

        // Hack
        if (rawOptions.size() < this.pageSize) {
            ytop -= height;
        }

        this.options = new ArrayList<>();
        this.buttonsX = new HashMap<>();
        this.buttonsY = new HashMap<>();
        for (int i = 0; i < this.pageSize; i++) {
            Button btn = new Button(screen, xtop, ytop + height * 2 + i * height, width + 1, height, "") {
                @Override
                public void onClick(Player.Hand hand) {
                    ListSelector.this.currentValue = ListSelector.this.usableButtons.get(this);
                    ListSelector.this.onClick(ListSelector.this.currentValue);
                    ListSelector.this.updateSearch(ListSelector.this.search.getText());
                }
            };
            this.buttonsX.put(btn, xtop);
            this.buttonsY.put(btn, ytop + height * 2 + i * height);
            this.options.add(btn);
        }

        this.search.setValidator(s -> {
            this.page = 0;
            this.updateSearch(s);
            return true;
        });
        this.updateSearch("");

        this.setVisible(false);
    }

    void updateSearch(String search) {
        Collection<String> names = search.isEmpty() ? this.rawOptions.keySet() : this.rawOptions.keySet().stream()
                .filter(v -> v.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        int nPages = this.pageSize > 0 ? (int) Math.ceil(names.size() / (float) this.pageSize) : 0;
        if (this.page >= nPages) {
            this.page = 0;
        }
        if (this.page < 0) {
            this.page = nPages - 1;
        }

        this.pagination.setText(String.format("Page %s of %s", this.page + 1, Math.max(1, nPages)));

        this.options.forEach(b -> {
            b.setText("");
            b.setVisible(false);
            b.setEnabled(false);
        });

        this.usableButtons = new HashMap<>();
        int bid = 0;
        for (Map.Entry<String, T> entry : this.rawOptions.entrySet().stream()
                .filter(e -> names.contains(e.getKey()))
                .skip((long) this.page * this.pageSize).limit(this.pageSize)
                .collect(Collectors.toList())) {
            Button button = this.options.get(bid);
            button.setEnabled(true);
            button.setVisible(true);
            String text = fitString(entry.getKey(), (int) Math.floor(this.width / 6.0));
            if (entry.getValue() == this.currentValue) {
                text = TextColor.YELLOW.wrap(text);
            }
            button.setText(text);
            this.usableButtons.put(button, entry.getValue());

            bid++;
        }
    }

    public abstract void onClick(T option);

    public void render(ButtonRenderer<T> renderer) {
        if (!this.isVisible()) {
            return;
        }

        for (Map.Entry<Button, T> entry : this.usableButtons.entrySet()) {
            renderer.render(entry.getKey(), GUIHelpers.getScreenWidth() / 2 + this.buttonsX.get(entry.getKey()), GUIHelpers.getScreenHeight() / 4 + this.buttonsY.get(entry.getKey()), entry.getValue());
        }
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        this.search.setVisible(visible && this.rawOptions.size() > this.pageSize);
        this.pagination.setVisible(visible && this.rawOptions.size() > this.pageSize);
        this.options.forEach(b -> b.setVisible(visible && !b.getText().isEmpty()));
    }

    @FunctionalInterface
    public interface ButtonRenderer<T> {
        void render(Button button, int x, int y, T value);
    }
}
