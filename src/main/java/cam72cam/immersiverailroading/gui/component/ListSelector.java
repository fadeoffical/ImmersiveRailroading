package cam72cam.immersiverailroading.gui.component;

import cam72cam.immersiverailroading.gui.ClickListHelper;
import cam72cam.mod.entity.Player;
import cam72cam.mod.gui.helpers.GUIHelpers;
import cam72cam.mod.gui.screen.Button;
import cam72cam.mod.gui.screen.IScreenBuilder;
import cam72cam.mod.gui.screen.TextField;
import cam72cam.mod.text.TextColor;

import java.util.*;
import java.util.stream.Collectors;

import static cam72cam.immersiverailroading.gui.component.GuiUtils.fitString;

public abstract class ListSelector<T> {

    private final Map<String, T> rawOptions;

    private final int width;

    private final int pageSize;

    private boolean visible;
    private int page;

    private T currentValue;

    private final TextField search;
    private final Button pagination;
    private final List<Button> options;

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
                // handle left and right click. Left click is next page, right click is previous page
                ListSelector.this.page += ClickListHelper.clickIndex(hand);
                ListSelector.this.updateSearch(ListSelector.this.search.getText());
            }
        };

        this.pageSize = Math.max(1, GUIHelpers.getScreenHeight() / height - 2);

        // Hack
        if (rawOptions.size() < this.pageSize) {
            ytop -= height;
        }

        this.options = new ArrayList<>();
        this.buttonsX = new HashMap<>();
        this.buttonsY = new HashMap<>();

        for (int i = 0; i < this.pageSize; i++) {
            Button button = new Button(screen, xtop, ytop + height * 2 + i * height, width + 1, height, "") {
                @Override
                public void onClick(Player.Hand hand) {
                    ListSelector.this.currentValue = ListSelector.this.usableButtons.get(this);
                    ListSelector.this.onClick(ListSelector.this.currentValue);
                    ListSelector.this.updateSearch(ListSelector.this.search.getText());
                }
            };
            this.buttonsX.put(button, xtop);
            this.buttonsY.put(button, ytop + height * 2 + i * height);
            this.options.add(button);
        }

        this.search.setValidator(s -> {
            this.page = 0;
            this.updateSearch(s);
            return true;
        });
        this.updateSearch("");

        this.setVisible(false);
    }

    private void updateSearch(String search) {
        final String searchLower = search.toLowerCase();
        Collection<String> names = search.isEmpty() ? this.rawOptions.keySet() : this.rawOptions.keySet()
                .stream()
                .map(String::toLowerCase)
                .filter(option -> option.contains(searchLower))
                .collect(Collectors.toList());

        int numberOfPages = this.pageSize > 0 ? (int) Math.ceil(names.size() / (float) this.pageSize) : 0;
        if (this.page >= numberOfPages) {
            this.page = 0;
        }
        if (this.page < 0) {
            this.page = numberOfPages - 1;
        }

        this.pagination.setText(String.format("Page %s of %s", this.page + 1, Math.max(1, numberOfPages)));

        this.options.forEach(button -> {
            button.setText("");
            button.setVisible(false);
            button.setEnabled(false);
        });

        this.usableButtons = new HashMap<>();

        // todo: this is shit; fix
        final int[] buttonId = {0};
        this.rawOptions.entrySet().stream()
                .filter(e -> names.contains(e.getKey()))
                .skip((long) this.page * this.pageSize)
                .limit(this.pageSize)
                .forEach(entry -> {
                    Button button = this.options.get(buttonId[0]);
                    button.setEnabled(true);
                    button.setVisible(true);

                    String text = fitString(entry.getKey(), (int) Math.floor(this.width / 6.0d));
                    if (entry.getValue() == this.currentValue) text = TextColor.YELLOW.wrap(text);
                    button.setText(text);

                    this.usableButtons.put(button, entry.getValue());
                    buttonId[0]++;
                });

    }

    public abstract void onClick(T option);

    public void render(ButtonRenderer<T> renderer) {
        if (!this.isVisible()) return;

        this.usableButtons.forEach((button, text) -> {
            int x = GUIHelpers.getScreenWidth() / 2 + this.buttonsX.get(button);
            int y = GUIHelpers.getScreenHeight() / 4 + this.buttonsY.get(button);
            renderer.render(button, x, y, text);
        });
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        this.search.setVisible(visible && this.rawOptions.size() > this.pageSize);
        this.pagination.setVisible(visible && this.rawOptions.size() > this.pageSize);
        this.options.forEach(button -> button.setVisible(visible && !button.getText().isEmpty()));
    }

    @FunctionalInterface
    public interface ButtonRenderer<T> {
        void render(Button button, int x, int y, T value);
    }
}
