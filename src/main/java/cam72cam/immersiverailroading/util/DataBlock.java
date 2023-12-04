package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.resource.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
public interface DataBlock {
    static DataBlock load(Identifier ident) throws IOException {
        return load(ident, false, null);
    }

    static DataBlock load(Identifier ident, boolean last, DataBlock parameters) throws IOException {
        InputStream stream = last ? ident.getLastResourceStream() : ident.getResourceStream();

        if (parameters != null) {
            String input = IOUtils.toString(stream, Charset.defaultCharset());
            for (String key : parameters.getValueMap().keySet()) {
                input = input.replace(key, parameters.getValue(key).asString());
            }
            stream = IOUtils.toInputStream(input, Charset.defaultCharset());
        }


        if (ident.getPath().toLowerCase(Locale.ROOT).endsWith(".caml")) {
            return CAML.parse(stream);
        }
        if (!ident.getPath().toLowerCase(Locale.ROOT).endsWith(".json")) {
            ImmersiveRailroading.warn("Unexpected file extension '%s', trying JSON...", ident.toString());
        }
        return JSON.parse(stream);
    }

    Map<String, Value> getValueMap();

    default Value getValue(String key) {
        return this.getValueMap().getOrDefault(key, Value.NULL);
    }

    static DataBlock load(Identifier ident, DataBlock parameters) throws IOException {
        return load(ident, false, parameters);
    }

    default DataBlock getBlock(String key) {
        return this.getBlockMap().get(key);
    }

    Map<String, DataBlock> getBlockMap();

    default List<DataBlock> getBlocks(String key) {
        return this.getBlocksMap().get(key);
    }

    Map<String, List<DataBlock>> getBlocksMap();

    default List<Value> getValues(String key) {
        return this.getValuesMap().get(key);
    }

    Map<String, List<Value>> getValuesMap();

    interface Value {
        Value NULL = new Value() {
            @Override
            public Boolean asBoolean() {
                return null;
            }

            @Override
            public Integer asInteger() {
                return null;
            }

            @Override
            public Float asFloat() {
                return null;
            }

            @Override
            public Double asDouble() {
                return null;
            }

            @Override
            public String asString() {
                return null;
            }
        };

        default boolean asBoolean(boolean fallback) {
            Boolean val = this.asBoolean();
            return val != null ? val : fallback;
        }

        Boolean asBoolean();

        default int asInteger(int fallback) {
            Integer val = this.asInteger();
            return val != null ? val : fallback;
        }

        Integer asInteger();

        default float asFloat(float fallback) {
            Float val = this.asFloat();
            return val != null ? val : fallback;
        }

        Float asFloat();

        default double asDouble(double fallback) {
            Double val = this.asDouble();
            return val != null ? val : fallback;
        }

        Double asDouble();

        default String asString(String fallback) {
            String val = this.asString();
            return val != null ? val : fallback;
        }

        String asString();

        default Identifier asIdentifier(Identifier fallback) {
            Identifier val = this.asIdentifier();
            return val != null && val.canLoad() ? val : fallback;
        }

        default Identifier asIdentifier() {
            String value = this.asString();
            return value != null ? new Identifier(ImmersiveRailroading.MODID, new Identifier(value).getPath()) : null;
        }
    }
}
