package cam72cam.immersiverailroading.gui.overlay;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.util.MergedBlocks;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.config.ConfigFile;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.gui.helpers.GUIHelpers;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.net.Packet;
import cam72cam.mod.render.opengl.BlendMode;
import cam72cam.mod.render.opengl.DirectDraw;
import cam72cam.mod.render.opengl.RenderState;
import cam72cam.mod.render.opengl.Texture;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.TagField;
import util.Matrix4;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class GuiBuilder {
    private static GuiBuilder target = null;
    private final float x;
    private final float y;
    private final Horizontal screen_x;
    private final Vertical screen_y;
    private final Identifier image;
    private final int width;
    private final int height;
    private final String text;
    private final float textHeight;
    private final Readouts readout;
    private final String control;
    private final String setting;
    private final String texture_variant;
    private final Float setting_default;
    private final boolean global;
    private final boolean invert;
    private final boolean translucent;
    private final boolean toggle;
    private final float tlx;
    private final float tly;
    private final float rotx;
    private final float roty;
    private final float rotdeg;
    private final float rotoff;
    private final Float scalex;
    private final Float scaley;
    private final Map<Float, Integer> colors = new HashMap<>();
    private final EntityRollingStockDefinition.ControlSoundsDefinition sound;
    private final List<GuiBuilder> elements;
    private float temporary_value = 0.5f;

    protected GuiBuilder(DataBlock data) throws IOException {
        data = processImports(data);

        // common stuff
        this.x = data.getValue("x").asFloat(0f);
        this.y = data.getValue("y").asFloat(0f);
        this.screen_x = Horizontal.from(data.getValue("screen_x").asString());
        this.screen_y = Vertical.from(data.getValue("screen_y").asString());

        // Text stuff
        DataBlock txt = data.getBlock("text");
        if (txt != null) {
            this.text = txt.getValue("value").asString();
            this.textHeight = txt.getValue("height").asFloat(0f);
        } else {
            this.text = null;
            this.textHeight = 0;
        }

        // Image stuff
        this.image = data.getValue("image").asIdentifier(null);
        if (this.image != null) {
            BufferedImage tmp = ImageIO.read(this.image.getResourceStream());
            this.width = tmp.getWidth();
            this.height = tmp.getHeight();
        } else if (this.text != null) {
            this.width = (int) (this.textHeight / 4 * this.text.length()); // Guesstimate
            this.height = (int) this.textHeight;
        } else {
            this.width = 0;
            this.height = 0;
        }

        // Controls
        String readout = data.getValue("readout").asString();
        this.readout = readout != null ? Readouts.valueOf(readout.toUpperCase(Locale.ROOT)) : null;
        this.control = data.getValue("control").asString();
        this.setting = data.getValue("setting").asString();
        this.setting_default = data.getValue("setting_default").asFloat();
        this.texture_variant = data.getValue("texture_variant").asString();
        DataBlock soundBlock = data.getBlock("sound");
        this.sound = soundBlock != null ? new EntityRollingStockDefinition.ControlSoundsDefinition(soundBlock) : null;
        this.global = data.getValue("global").asBoolean(false);
        this.invert = data.getValue("invert").asBoolean(false);
        this.translucent = data.getValue("translucent").asBoolean(data.getValue("hide").asBoolean(false));
        this.toggle = data.getValue("toggle").asBoolean(false);

        DataBlock tl = data.getBlock("translate");
        if (tl != null) {
            this.tlx = tl.getValue("x").asFloat(0);
            this.tly = tl.getValue("y").asFloat(0);
        } else {
            this.tlx = this.tly = 0;
        }

        DataBlock rot = data.getBlock("rotate");
        if (rot != null) {
            this.rotx = rot.getValue("x").asFloat(0);
            this.roty = rot.getValue("y").asFloat(0);
            this.rotdeg = rot.getValue("degrees").asFloat(360);
            this.rotoff = rot.getValue("offset").asFloat(0);
        } else {
            this.rotx = 0;
            this.roty = 0;
            this.rotdeg = 0;
            this.rotoff = 0;
        }

        DataBlock scale = data.getBlock("scale");
        if (scale != null) {
            this.scalex = scale.getValue("x").asFloat();
            this.scaley = scale.getValue("y").asFloat();
        } else {
            this.scalex = null;
            this.scaley = null;
        }

        DataBlock color = data.getBlock("color");
        if (color != null) {
            color.getValueMap().forEach((key, value) -> {
                String hex = value.asString();
                if (hex.length() == 8) {
                    hex = hex.replace("0x", "0xFF");
                    hex = hex.replace("0X", "0XFF");
                }
                this.colors.put(Float.parseFloat(key), (int) (long) Long.decode(hex));
            });
        }

        this.elements = new ArrayList<>();

        // Children
        List<DataBlock> elem = data.getBlocks("elements");
        if (elem == null) {
            elem = data.getBlocks("element");
        }
        if (elem != null) {
            for (DataBlock element : elem) {
                this.elements.add(new GuiBuilder(element));
            }
        }
    }

    private static DataBlock processImports(DataBlock data) throws IOException {
        // This is kinda weird as it appends the import to the current block instead of inserting it in the current
        // element list.  It's definitely a bit of a footgun.  The "correct" way to do this would be to make import part
        // of parsing in CAML, which is its own sort of weirdness due to JSON interop.
        List<DataBlock.Value> direct = data.getValues("import");
        if (direct != null) {
            for (DataBlock.Value imp : direct) {
                data = new MergedBlocks(data, processImports(DataBlock.load(imp.asIdentifier())));
            }
        }
        List<DataBlock> imports = data.getBlocks("import");
        if (imports != null) {
            for (DataBlock imp : imports) {
                data = new MergedBlocks(data, processImports(DataBlock.load(imp.getValue("source")
                        .asIdentifier(), imp.getBlock("replace"))));
            }
        }
        return data;
    }

    public static GuiBuilder parse(Identifier overlay) throws IOException {
        return new GuiBuilder(DataBlock.load(overlay));
    }

    public static void onClientTick() {
        if (target == null || target.readout != Readouts.TRAIN_BRAKE_LEVER) return;
        if (!MinecraftClient.isReady()) return;

        Entity riding = MinecraftClient.getPlayer().getRiding();
        if (!(riding instanceof EntityRollingStock)) return;

        EntityRollingStock stock = (EntityRollingStock) riding;
        float value = target.invert ? target.getValue(stock) : 1 - target.getValue(stock);
        new ControlChangePacket(stock, target.readout, target.control, target.global, target.texture_variant, value).sendToServer();
    }

    private float getValue(EntityRollingStock stock) {
        float value = 0;
        if (this.readout != null) {
            value = this.readout.getValue(stock, this.temporary_value);
        } else if (this.control != null) {
            value = stock.getControlPosition(this.control);
        } else if (this.setting != null) {
            if (!ConfigGraphics.settings.containsKey(this.setting) && this.setting_default != null) {
                ConfigGraphics.settings.put(this.setting, this.setting_default);
            }

            value = ConfigGraphics.settings.getOrDefault(this.setting, 0f);
        } else if (this.texture_variant != null) {
            value = this.texture_variant.equals(stock.getTexture()) ? 1 : 0;
        }

        if (this.invert) {
            value = 1 - value;
        }

        return value;
    }

    private void applyPosition(Matrix4 matrix, int maxx, int maxy) {
        matrix.translate(this.x, this.y, 0);

        switch (this.screen_x) {
            case LEFT:
                // NOP
                break;
            case MIDDLE:
                matrix.translate(maxx / 2f, 0, 0);
                break;
            case RIGHT:
                matrix.translate(maxx, 0, 0);
                break;
        }
        switch (this.screen_y) {
            case TOP:
                // NOP
                break;
            case MIDDLE:
                matrix.translate(0, maxy / 2f, 0);
                break;
            case BOTTOM:
                matrix.translate(0, maxy, 0);
                break;
        }
    }

    private void applyValue(Matrix4 matrix, float value) {
        if (this.tlx != 0 || this.tly != 0) {
            matrix.translate(this.tlx * value, this.tly * value, 0);
        }
        if (this.rotdeg != 0) {
            matrix.translate(this.rotx, this.roty, 0);
            matrix.rotate(Math.toRadians(this.rotdeg * value + this.rotoff), 0, 0, 1);
            matrix.translate(-this.rotx, -this.roty, 0);
        }
        if (this.scalex != null || this.scaley != null) {
            matrix.scale(this.scalex != null ? this.scalex * value : 1, this.scaley != null ? this.scaley * value : 1, 1);
        }
    }

    public void render(RenderState state, EntityRollingStock stock) {
        this.render(stock, state.clone()
                .color(1, 1, 1, 1), GUIHelpers.getScreenWidth(), GUIHelpers.getScreenHeight(), 0xFFFFFFFF);
    }

    private void render(EntityRollingStock stock, RenderState state, int maxx, int maxy, int baseColor) {
        float value = this.getValue(stock);
        if (this.translucent) {
            if (value == 0) {
                return;
            }
            float alpha = (baseColor >> 24 & 255) / 255f * value;
            baseColor = baseColor & 0x00FFFFFF | ((int) (alpha * 255f) << 24);
        }

        state = state.clone(); // TODO mem opt?
        this.applyPosition(state.model_view(), maxx, maxy);
        this.applyValue(state.model_view(), value);

        Float colorKey = null;
        for (float key : this.colors.keySet()) {
            if (key <= value && (colorKey == null || key > colorKey)) {
                colorKey = key;
            }
        }

        if (colorKey != null && this.colors.containsKey(colorKey)) {
            float oldAlpha = (baseColor >> 24 & 255) / 255f;

            int newColor = this.colors.get(colorKey);
            float newAlpha = (newColor >> 24 & 255) / 255f;
            baseColor = newColor & 0x00FFFFFF | ((int) (newAlpha * oldAlpha * 255f) << 24);
        }

        if (colorKey != null || this.translucent) {
            state.color((baseColor >> 16 & 255) / 255.0f, (baseColor >> 8 & 255) / 255.0f, (baseColor & 255) / 255.0f, (baseColor >> 24 & 255) / 255.0f);
        }

        if (this.image != null) {
            DirectDraw draw = new DirectDraw();
            draw.vertex(0, 0, 0).uv(0, 0);
            draw.vertex(0, this.height, 0).uv(0, 1);
            draw.vertex(this.width, this.height, 0).uv(1, 1);
            draw.vertex(this.width, 0, 0).uv(1, 0);
            draw.draw(state.clone()
                    .texture(Texture.wrap(this.image))
                    .alpha_test(false)
                    .blend(new BlendMode(BlendMode.GL_SRC_ALPHA, BlendMode.GL_ONE_MINUS_SRC_ALPHA))
            );
        }
        if (this.text != null) {
            String out = this.text;
            for (Stat stat : Stat.values()) {
                if (out.contains(stat.toString())) {
                    out = out.replace(stat.toString(), stat.getValue(stock));
                }
            }
            for (GuiText label : new GuiText[]{GuiText.LABEL_THROTTLE, GuiText.LABEL_REVERSER, GuiText.LABEL_BRAKE}) {
                out = out.replace(label.getValue(), label.toString());
            }
            // Text is 8px tall
            float scale = this.textHeight / 8f;
            Matrix4 mat = state.model_view().copy();
            mat.scale(scale, scale, scale);
            GUIHelpers.drawCenteredString(out, 0, 0, baseColor, mat);
        }
        for (GuiBuilder element : this.elements) {
            element.render(stock, state, maxx, maxy, baseColor);
        }
    }

    private GuiBuilder find(EntityRollingStock stock, Matrix4 matrix, int maxx, int maxy, int x, int y) {
        float value = this.getValue(stock);
        if (this.translucent && value == 0) {
            return null;
        }
        matrix = matrix.copy(); // TODO mem opt?
        this.applyPosition(matrix, maxx, maxy);
        this.applyValue(matrix, value);
        for (GuiBuilder element : this.elements) {
            GuiBuilder found = element.find(stock, matrix, maxx, maxy, x, y);
            if (found != null) {
                return found;
            }
        }

        if (this.interactable() && (this.image != null || this.text != null)) {
            if (this.control == null && this.setting == null && this.texture_variant == null) {
                if (this.readout == null) {
                    return null;
                }
                switch (this.readout) {
                    case THROTTLE:
                    case REVERSER:
                    case TRAIN_BRAKE:
                    case TRAIN_BRAKE_LEVER:
                    case INDEPENDENT_BRAKE:
                    case COUPLER_FRONT:
                    case COUPLER_REAR:
                    case BELL:
                    case WHISTLE:
                    case HORN:
                    case ENGINE:
                        break;
                    default:
                        return null;
                }
            }
            int border = 2;
            Vec3d cornerA = matrix.apply(new Vec3d((this.image == null ? -this.width : 0) - border, -border, 0));
            Vec3d cornerB = matrix.apply(new Vec3d((this.image == null ? -this.width : 0) - border, this.height + border, 0));
            Vec3d cornerC = matrix.apply(new Vec3d(this.width + border, -border, 0));
            Vec3d cornerD = matrix.apply(new Vec3d(this.width + border, this.height + border, 0));

            Polygon poly = new Polygon(
                    new int[]{(int) cornerA.x, (int) cornerB.x, (int) cornerC.x, (int) cornerD.x},
                    new int[]{(int) cornerA.y, (int) cornerB.y, (int) cornerC.y, (int) cornerD.y},
                    4
            );
            if (poly.getBounds2D().contains(x, y)) {
                return this;
            }
        }
        return null;
    }

    private boolean interactable() {
        return this.tlx != 0 || this.tly != 0 || this.rotdeg != 0 || this.scalex != null || this.scaley != null || this.toggle;
    }

    private void onMouseClick(EntityRollingStock stock) {
        if (this.sound != null) {
            this.sound.effects(stock, true, this.getValue(stock), MinecraftClient.getPlayer().getPosition());
        }
    }

    private void onMouseMove(EntityRollingStock stock, Matrix4 matrix, GuiBuilder target, int maxx, int maxy, int x, int y) {
        float value = this.getValue(stock);
        matrix = matrix.copy(); // TODO mem opt?
        this.applyPosition(matrix, maxx, maxy);
        Matrix4 preApply = matrix.copy();
        this.applyValue(matrix, value);

        if (target == this) {
            // 0 0 imageHeight imageWidth

            if (this.toggle) {
                return;
            }

            float closestValue = value;
            double closestDelta = 999999;

            for (float checkValue = 0; checkValue <= 1; checkValue += 0.01) {
                Matrix4 temp = preApply.copy();
                if (this.tlx != 0 || this.tly != 0) {
                    temp.translate(this.tlx * checkValue, this.tly * checkValue, 0);
                }
                if (this.rotdeg != 0) {
                    temp.translate(this.rotx, this.roty, 0);
                    temp.rotate(Math.toRadians(this.rotdeg * checkValue + this.rotoff), 0, 0, 1);
                    temp.translate(-this.rotx, -this.roty, 0);
                }
                if (this.scalex != null || this.scaley != null) {
                    temp.scale(this.scalex != null ? this.scalex * checkValue : 1, this.scaley != null ? this.scaley * checkValue : 1, 1);
                }

                Vec3d checkMiddle = temp.apply(new Vec3d(this.width / 2f, this.height / 2f, 0));
                double delta = checkMiddle.distanceTo(new Vec3d(x, y, 0));
                if (delta < closestDelta) {
                    closestDelta = delta;
                    closestValue = checkValue;
                }
            }

            if (closestValue != value) {
                float val = this.invert ? 1 - closestValue : closestValue;
                this.temporary_value = val;
                if (this.setting != null) {
                    ConfigGraphics.settings.put(this.setting, val);
                    ConfigFile.write(ConfigGraphics.class);
                } else {
                    if (this.readout != Readouts.TRAIN_BRAKE_LEVER) {
                        new ControlChangePacket(stock, this.readout, this.control, this.global, this.texture_variant, val).sendToServer();
                    }
                }
            }

            if (target.sound != null) {
                target.sound.effects(stock, true, target.getValue(stock), MinecraftClient.getPlayer().getPosition());
            }
        } else {
            for (GuiBuilder element : this.elements) {
                element.onMouseMove(stock, matrix, target, maxx, maxy, x, y);
            }
        }
    }

    public boolean onMouseScroll(double scroll, EntityRollingStock stock, int maxx, int maxy, int x, int y) {
        GuiBuilder target = this.find(stock, new Matrix4(), maxx, maxy, x, y);
        if (target != null && !target.toggle) {
            target.onMouseClick(stock);

            float value = target.getValue(stock);

            // Same as ClientPartDragging
            value += scroll / -50 * ConfigGraphics.ScrollSpeed;

            if (target.setting != null) {
                ConfigGraphics.settings.put(target.setting, value);
                ConfigFile.write(ConfigGraphics.class);
            } else {
                if (target.readout != Readouts.TRAIN_BRAKE_LEVER) {
                    new ControlChangePacket(stock, target.readout, target.control, target.global, target.texture_variant, value).sendToServer();
                }
            }

            return false;
        }
        return true;
    }

    public void onMouseRelease(EntityRollingStock stock) {
        float value = this.getValue(stock);

        if (this.sound != null) {
            this.sound.effects(stock, false, value, MinecraftClient.getPlayer().getPosition());
        }

        if (this.toggle) {
            value = 1 - value;
            if (this.invert) {
                value = 1 - value;
            }
        }

        if (this.readout == Readouts.HORN || this.readout == Readouts.WHISTLE) {
            value = 0;
        }

        if (this.setting != null) {
            ConfigGraphics.settings.put(this.setting, value);
            ConfigFile.write(ConfigGraphics.class);
        } else {
            new ControlChangePacket(stock, this.readout, this.control, this.global, this.texture_variant, value).sendToServer();
        }

        this.temporary_value = 0.5f;
    }

    public boolean click(ClientEvents.MouseGuiEvent event, EntityRollingStock stock) {
        switch (event.action) {
            case CLICK:
                if (target != null) {
                    // Weird hack for when mouse goes out of the window
                    target.onMouseRelease(stock);
                }
                target = this.find(stock, new Matrix4(), GUIHelpers.getScreenWidth(), GUIHelpers.getScreenHeight(), event.x, event.y);
                if (target != null) {
                    target.onMouseClick(stock);
                }
                return target == null;
            case RELEASE:
                if (target != null) {
                    target.onMouseRelease(stock);
                    target = null;
                    return false;
                }
                break;
            case MOVE:
                if (target != null) {
                    this.onMouseMove(stock, new Matrix4(), target, GUIHelpers.getScreenWidth(), GUIHelpers.getScreenHeight(), event.x, event.y);
                    return false;
                }
                break;
            case SCROLL:
                return this.onMouseScroll(event.scroll, stock, GUIHelpers.getScreenWidth(), GUIHelpers.getScreenHeight(), event.x, event.y);
        }
        return true;
    }

    private enum Horizontal {
        LEFT,
        RIGHT,
        MIDDLE,
        ;

        public static Horizontal from(String pos_x) {
            if (pos_x == null) {
                return LEFT;
            }
            pos_x = pos_x.toUpperCase(Locale.ROOT);
            if (pos_x.equals("CENTER")) {
                return MIDDLE;
            }
            return valueOf(pos_x);
        }
    }

    private enum Vertical {
        TOP,
        MIDDLE,
        BOTTOM,
        ;

        public static Vertical from(String pos_y) {
            if (pos_y == null) {
                return TOP;
            }
            pos_y = pos_y.toUpperCase(Locale.ROOT);
            if (pos_y.equals("CENTER")) {
                return MIDDLE;
            }

            return valueOf(pos_y);
        }
    }

    public static class ControlChangePacket extends Packet {
        @TagField
        private UUID stockUUID;
        @TagField(typeHint = Readouts.class)
        private Readouts readout;
        @TagField
        private String controlGroup;
        @TagField
        private boolean global;
        @TagField
        private float value;

        @TagField
        private String texture_variant;

        public ControlChangePacket() {
            super(); // Reflection
        }

        public ControlChangePacket(EntityRollingStock stock, Readouts readout, String controlGroup, boolean global, String texture_variant, float value) {
            this.stockUUID = stock.getUUID();
            this.readout = readout;
            this.controlGroup = controlGroup;
            this.global = global;
            this.texture_variant = texture_variant;
            this.value = value;
            // Update the UI, server will resync once the update actually happens
            this.update(stock);
        }

        public void update(EntityRollingStock stock) {
            // TODO permissions!
            if (this.controlGroup != null) {
                switch (this.controlGroup) {
                    case "REVERSERFORWARD":
                        this.readout = Readouts.REVERSER;
                        this.value = 1;
                        break;
                    case "REVERSERNEUTRAL":
                        this.readout = Readouts.REVERSER;
                        this.value = 0.5f;
                        break;
                    case "REVERSERBACKWARD":
                        this.readout = Readouts.REVERSER;
                        this.value = 0;
                        break;
                    default:
                        if (this.global) {
                            ((EntityCoupleableRollingStock) stock).mapTrain((EntityCoupleableRollingStock) stock, false, target -> {
                                target.setControlPosition(this.controlGroup, this.value);
                            });
                        } else {
                            stock.setControlPosition(this.controlGroup, this.value);
                        }
                        return;
                }
            }
            if (this.readout != null) {
                if (this.global) {
                    ((EntityCoupleableRollingStock) stock).mapTrain((EntityCoupleableRollingStock) stock, false, target -> {
                        this.readout.setValue(target, this.value);
                    });
                } else {
                    this.readout.setValue(stock, this.value);
                }
            }
            if (this.texture_variant != null && this.value == 1) {
                stock.setTexture(this.texture_variant);
            }
        }

        @Override
        protected void handle() {
            EntityRollingStock stock = this.getWorld().getEntity(this.stockUUID, EntityRollingStock.class);
            if (stock != null) {
                this.update(stock);
            }
        }
    }
}
